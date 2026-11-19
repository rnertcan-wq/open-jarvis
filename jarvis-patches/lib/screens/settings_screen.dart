import 'package:flutter/material.dart';
import '../services/api_client.dart';
import '../theme/app_theme.dart';
import 'memory_screen.dart';

class SettingsScreen extends StatefulWidget {
 const SettingsScreen({super.key,required this.api}); final ApiClient api;
 @override State<SettingsScreen> createState()=>_SettingsScreenState();
}
class _SettingsScreenState extends State<SettingsScreen>{
 final url=TextEditingController(),key=TextEditingController(),model=TextEditingController();
 bool busy=false,showKey=false;String status='EVREN API ayarlarını kaydedin.';
 @override void initState(){super.initState();url.text=widget.api.evrenBaseUrl;model.text=widget.api.evrenModel;}
 Future<void> save()async{if(url.text.trim().isEmpty||key.text.trim().isEmpty||model.text.trim().isEmpty){setState(()=>status='Adres, API anahtarı ve model alanlarını doldurun.');return;}setState(()=>busy=true);try{await widget.api.saveEvren(baseUrl:url.text,apiKey:key.text,model:model.text);key.clear();if(mounted)setState(()=>status='EVREN ayarları cihazda güvenli şekilde kaydedildi.');}catch(e){if(mounted)setState(()=>status='Kaydedilemedi: $e');}finally{if(mounted)setState(()=>busy=false);}}
 Future<void> test()async{setState(()=>busy=true);try{final models=await widget.api.testEvren();if(mounted)setState(()=>status='EVREN bağlantısı başarılı. Modeller: $models');}catch(e){if(mounted)setState(()=>status='Bağlantı testi başarısız: $e');}finally{if(mounted)setState(()=>busy=false);}}
 @override void dispose(){url.dispose();key.dispose();model.dispose();super.dispose();}
 @override Widget build(BuildContext context)=>ListView(padding:const EdgeInsets.all(18),children:[
 const Text('Ayarlar',style:TextStyle(fontSize:25,fontWeight:FontWeight.w800)),const SizedBox(height:6),const Text('EVREN API bağlantısı ve kişisel tercihler.',style:TextStyle(color:Colors.white54)),const SizedBox(height:18),
 Card(child:Padding(padding:const EdgeInsets.all(16),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
 const Text('EVREN API',style:TextStyle(fontWeight:FontWeight.w800,fontSize:17)),const SizedBox(height:8),const Text('EVREN panelindeki OpenAI uyumlu API adresini ve model kimliğini girin. Anahtar Android güvenli depolamasında tutulur.',style:TextStyle(color:Colors.white60,height:1.4)),
 const SizedBox(height:14),TextField(controller:url,keyboardType:TextInputType.url,decoration:const InputDecoration(labelText:'API Base URL',hintText:'https://.../v1')),
 const SizedBox(height:10),TextField(controller:key,obscureText:!showKey,decoration:InputDecoration(labelText:'EVREN API Key',suffixIcon:IconButton(onPressed:()=>setState(()=>showKey=!showKey),icon:Icon(showKey?Icons.visibility_off:Icons.visibility)))),
 const SizedBox(height:10),TextField(controller:model,decoration:const InputDecoration(labelText:'Model ID',hintText:'DeepSeek / diğer model kimliği')),
 const SizedBox(height:14),Row(children:[Expanded(child:OutlinedButton(onPressed:busy?null:test,child:const Text('Bağlantıyı test et'))),const SizedBox(width:10),Expanded(child:FilledButton(onPressed:busy?null:save,child:Text(busy?'Bekleyin…':'Kaydet')))]),
 ]))),
 const SizedBox(height:14),Card(child:ListTile(leading:const Icon(Icons.psychology_alt_outlined,color:AppTheme.cyan),title:const Text('Kalıcı hafıza'),subtitle:const Text('Kayıtları görüntüle ve düzenle'),trailing:const Icon(Icons.chevron_right),onTap:()=>Navigator.push(context,MaterialPageRoute(builder:(_)=>MemoryScreen(api:widget.api))))),
 const SizedBox(height:12),Container(padding:const EdgeInsets.all(14),decoration:BoxDecoration(color:AppTheme.panel,borderRadius:BorderRadius.circular(16)),child:Text(status,style:const TextStyle(color:Colors.white70,fontSize:12))),
 ]);
}
