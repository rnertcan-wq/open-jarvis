import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'package:image_picker/image_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:syncfusion_flutter_pdf/pdf.dart';
import '../services/api_client.dart';
import '../theme/app_theme.dart';

class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<ChatScreen> createState() => _ChatScreenState();
}
class _ChatScreenState extends State<ChatScreen> {
  final controller = TextEditingController();
  final scroll = ScrollController();
  final List<Map<String,dynamic>> messages = [];
  final List<Map<String,dynamic>> attachments = [];
  bool sending=false, loadingHistory=true;
  String? error, conversationId;
  String conversationTitle='Yeni sohbet';
  List<Map<String,dynamic>> history=[];
  @override
  void initState(){super.initState(); _loadHistory();}
  Future<void> _loadHistory() async {
    final p=await SharedPreferences.getInstance();
    final raw=p.getString('jarvis_conversations');
    if(raw!=null){try{history=List<Map<String,dynamic>>.from(jsonDecode(raw));}catch(_){}}
    if(mounted)setState(()=>loadingHistory=false);
  }
  Future<void> _persist() async {
    if(messages.isEmpty)return;
    final p=await SharedPreferences.getInstance();
    final id=conversationId ?? DateTime.now().microsecondsSinceEpoch.toString();
    conversationId=id;
    final item={'id':id,'title':conversationTitle,'updated':DateTime.now().toIso8601String(),'messages':messages};
    history.removeWhere((e)=>e['id']==id);
    history.insert(0,item);
    if(history.length>100)history=history.take(100).toList();
    await p.setString('jarvis_conversations',jsonEncode(history));
  }
  void _newChat(){setState((){messages.clear();attachments.clear();conversationId=null;conversationTitle='Yeni sohbet';error=null;});}
  Future<void> _openHistory() async {
    await _loadHistory();
    if(!mounted)return;
    showModalBottomSheet(context:context, isScrollControlled:true, backgroundColor:AppTheme.surface, builder:(ctx)=>SafeArea(child:SizedBox(height:MediaQuery.of(ctx).size.height*.72,child:Column(children:[
      Padding(padding:const EdgeInsets.all(16),child:Row(children:[const Expanded(child:Text('Sohbet geçmişi',style:TextStyle(fontSize:19,fontWeight:FontWeight.bold))),IconButton(onPressed:()=>Navigator.pop(ctx),icon:const Icon(Icons.close))])),
      Expanded(child:history.isEmpty?const Center(child:Text('Henüz kayıtlı sohbet yok.')):ListView.builder(itemCount:history.length,itemBuilder:(c,i){final h=history[i];return ListTile(leading:const Icon(Icons.chat_bubble_outline),title:Text(h['title']??'Sohbet'),subtitle:Text((h['updated']??'').toString(),maxLines:1),onTap:(){setState((){conversationId=h['id'];conversationTitle=h['title']??'Sohbet';messages..clear()..addAll(List<Map<String,dynamic>>.from(h['messages']));});Navigator.pop(ctx);});})),
    ]))));
  }
  Future<void> _chooseAttachment() async {
    showModalBottomSheet(context:context,backgroundColor:AppTheme.surface,builder:(ctx)=>SafeArea(child:Wrap(children:[
      ListTile(leading:const Icon(Icons.photo_library),title:const Text('Galeriden görsel'),onTap:()async{Navigator.pop(ctx);final x=await ImagePicker().pickImage(source:ImageSource.gallery,imageQuality:88);if(x!=null)await _addFile(x.path,x.name);}),
      ListTile(leading:const Icon(Icons.camera_alt),title:const Text('Kamerayla fotoğraf çek'),onTap:()async{Navigator.pop(ctx);final x=await ImagePicker().pickImage(source:ImageSource.camera,imageQuality:88);if(x!=null)await _addFile(x.path,x.name);}),
      ListTile(leading:const Icon(Icons.attach_file),title:const Text('PDF veya belge seç'),onTap:()async{Navigator.pop(ctx);final r=await FilePicker.platform.pickFiles(withData:true,type:FileType.custom,allowedExtensions:['pdf','txt','md','csv','json']);if(r!=null&&r.files.isNotEmpty){final f=r.files.first;await _addBytes(f.name,f.bytes ?? await File(f.path!).readAsBytes());}}),
    ])));
  }
  Future<void> _addFile(String path,String name)async{final bytes=await File(path).readAsBytes();await _addBytes(name,bytes);}
  Future<void> _addBytes(String name,List<int> bytes)async{
    final lower=name.toLowerCase();String mime=lower.endsWith('.png')?'image/png':lower.endsWith('.webp')?'image/webp':lower.endsWith('.jpg')||lower.endsWith('.jpeg')?'image/jpeg':lower.endsWith('.pdf')?'application/pdf':'text/plain';
    String? extracted;
    if(mime=='application/pdf'){final doc=PdfDocument(inputBytes:bytes);extracted=PdfTextExtractor(doc).extractText();doc.dispose();}
    else if(!mime.startsWith('image/')){extracted=utf8.decode(bytes,allowMalformed:true);}
    setState(()=>attachments.add({'name':name,'mime':mime,'base64':base64Encode(bytes),'extracted':extracted}));
  }
  Future<void> send() async {
    final text=controller.text.trim();if((text.isEmpty&&attachments.isEmpty)||sending)return;
    controller.clear();
    final current=List<Map<String,dynamic>>.from(attachments);attachments.clear();
    setState((){messages.add({'role':'user','content':text.isEmpty?'Ekli dosyayı incele.':text});sending=true;error=null;if(conversationTitle=='Yeni sohbet')conversationTitle=text.isEmpty?'Dosya sohbeti':text.length>34?'${text.substring(0,34)}…':text;});
    _scrollDown();
    try{
      final contextMessages=messages.map((m)=>{'role':m['role'],'content':m['content']}).toList();
      final answer=await widget.api.askEvren(contextMessages,attachments:current);
      if(mounted){setState(()=>messages.add({'role':'assistant','content':answer}));await _persist();}
    }catch(e){if(mounted){setState(()=>error='İstek başarısız: $e');messages.removeLast();}}
    finally{if(mounted)setState(()=>sending=false);_scrollDown();}
  }
  void _scrollDown(){WidgetsBinding.instance.addPostFrameCallback((_){if(scroll.hasClients)scroll.animateTo(scroll.position.maxScrollExtent,duration:const Duration(milliseconds:250),curve:Curves.easeOut);});}
  @override
  void dispose(){controller.dispose();scroll.dispose();super.dispose();}
  @override
  Widget build(BuildContext context)=>Column(children:[
    Padding(padding:const EdgeInsets.fromLTRB(20,14,12,12),child:Row(children:[
      Container(width:42,height:42,decoration:BoxDecoration(gradient:const LinearGradient(colors:[AppTheme.accent,AppTheme.cyan]),borderRadius:BorderRadius.circular(14)),child:const Icon(Icons.bolt,color:Colors.white)),
      const SizedBox(width:12),const Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text('JARVIS AI',style:TextStyle(fontWeight:FontWeight.w800,fontSize:18,letterSpacing:1.2)),Text('EVREN destekli kişisel asistan',style:TextStyle(color:Colors.white54,fontSize:12))])),
      IconButton(onPressed:_openHistory,icon:const Icon(Icons.history),tooltip:'Sohbet geçmişi'),IconButton(onPressed:_newChat,icon:const Icon(Icons.add_comment_outlined),tooltip:'Yeni sohbet'),
    ])),
    Expanded(child:messages.isEmpty?_welcome():ListView.builder(controller:scroll,padding:const EdgeInsets.all(16),itemCount:messages.length+(sending?1:0),itemBuilder:(context,i){
      if(i==messages.length)return const Align(alignment:Alignment.centerLeft,child:Padding(padding:EdgeInsets.all(12),child:Text('JARVIS düşünüyor…',style:TextStyle(color:AppTheme.cyan))));
      final item=messages[i];final mine=item['role']=='user';
      return Align(alignment:mine?Alignment.centerRight:Alignment.centerLeft,child:Container(margin:const EdgeInsets.only(bottom:12),padding:const EdgeInsets.all(14),constraints:const BoxConstraints(maxWidth:340),decoration:BoxDecoration(color:mine?const Color(0xFF302C59):AppTheme.surface,borderRadius:BorderRadius.circular(18)),child:SelectableText(item['content']?.toString()??'',style:const TextStyle(height:1.45))));
    })),
    if(error!=null)Padding(padding:const EdgeInsets.symmetric(horizontal:16,vertical:4),child:Text(error!,style:const TextStyle(color:Colors.redAccent,fontSize:12))),
    if(attachments.isNotEmpty)SizedBox(height:62,child:ListView(scrollDirection:Axis.horizontal,children:attachments.map((a)=>Padding(padding:const EdgeInsets.symmetric(horizontal:5),child:Chip(avatar:Icon(a['mime'].toString().startsWith('image/')?Icons.image:Icons.description,size:17),label:Text(a['name'],overflow:TextOverflow.ellipsis),onDeleted:()=>setState(()=>attachments.remove(a))))).toList())),
    Padding(padding:const EdgeInsets.fromLTRB(14,8,14,14),child:Row(children:[
      IconButton(onPressed:_chooseAttachment,icon:const Icon(Icons.add_circle_outline,size:28),tooltip:'Dosya veya görsel ekle'),
      Expanded(child:TextField(controller:controller,minLines:1,maxLines:5,textInputAction:TextInputAction.send,onSubmitted:(_)=>send(),decoration:const InputDecoration(hintText:'JARVIS’e bir şey sor…'))),
      const SizedBox(width:8),SizedBox(width:52,height:52,child:FilledButton(onPressed:sending?null:send,style:FilledButton.styleFrom(padding:EdgeInsets.zero,shape:RoundedRectangleBorder(borderRadius:BorderRadius.circular(16))),child:sending?const SizedBox(width:18,height:18,child:CircularProgressIndicator(strokeWidth:2)):const Icon(Icons.arrow_upward))),
    ])),
  ]);
  Widget _welcome()=>Center(child:Padding(padding:const EdgeInsets.all(28),child:Column(mainAxisSize:MainAxisSize.min,children:[
    Container(width:92,height:92,decoration:BoxDecoration(shape:BoxShape.circle,color:AppTheme.panel,border:Border.all(color:AppTheme.accent.withOpacity(.45))),child:const Icon(Icons.auto_awesome,size:42,color:AppTheme.cyan)),
    const SizedBox(height:22),const Text('Nasıl yardımcı olabilirim?',style:TextStyle(fontSize:22,fontWeight:FontWeight.w700)),const SizedBox(height:8),const Text('EVREN modeline bağlanın, konuşmalarınızı kaydedin ve görsel ya da dosya ekleyin.',textAlign:TextAlign.center,style:TextStyle(color:Colors.white54,height:1.5)),
  ])));
}
