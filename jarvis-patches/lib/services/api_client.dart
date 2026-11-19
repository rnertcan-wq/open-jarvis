import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class ApiClient {
  ApiClient() : _dio = Dio(BaseOptions(connectTimeout: const Duration(seconds: 20), receiveTimeout: const Duration(minutes: 3))) {
    _dio.interceptors.add(InterceptorsWrapper(onRequest: (options, handler) async {
      final token = await _storage.read(key: 'jarvis_token');
      if (token != null && token.isNotEmpty) options.headers['Authorization'] = 'Bearer $token';
      handler.next(options);
    }));
  }
  final Dio _dio;
  final FlutterSecureStorage _storage = const FlutterSecureStorage();
  String _baseUrl = 'http://10.0.2.2:8080';
  String evrenBaseUrl = 'https://evren-llmapi.ssyz.org.tr/v1';
  String evrenModel = '';
  String get baseUrl => _baseUrl;
  Future<void> initialize() async {
    _baseUrl = await _storage.read(key: 'jarvis_base_url') ?? _baseUrl;
    evrenBaseUrl = await _storage.read(key: 'evren_base_url') ?? evrenBaseUrl;
    evrenModel = await _storage.read(key: 'evren_model') ?? '';
    _dio.options.baseUrl = _baseUrl;
  }
  Future<void> setBaseUrl(String value) async {
    _baseUrl = value.trim().replaceAll(RegExp(r'/$'), '');
    _dio.options.baseUrl = _baseUrl;
    await _storage.write(key: 'jarvis_base_url', value: _baseUrl);
  }
  Future<void> saveEvren({required String baseUrl, required String apiKey, required String model}) async {
    evrenBaseUrl = baseUrl.trim().replaceAll(RegExp(r'/+
    evrenModel = model.trim();
    await _storage.write(key: 'evren_base_url', value: evrenBaseUrl);
    await _storage.write(key: 'evren_api_key', value: apiKey.trim());
    await _storage.write(key: 'evren_model', value: evrenModel);
  }
  Future<String?> readEvrenKey() => _storage.read(key: 'evren_api_key');
  Future<void> clearEvrenKey() => _storage.delete(key: 'evren_api_key');
  Future<String> testEvren() async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) throw Exception('Önce EVREN API anahtarını kaydedin.');
    final d = Dio(BaseOptions(baseUrl: '${evrenBaseUrl.replaceAll(RegExp(r'/+
    final response = await d.get('models', options: Options(headers: {'Authorization': 'Bearer $key', 'X-API-Key': key}));
    final data = response.data;
    if (data is Map && data['data'] is List) return (data['data'] as List).map((e) => e is Map ? e['id'].toString() : '').where((e) => e.isNotEmpty).take(30).join('\n');
    return data.toString();
  }
  Future<String> askEvren(List<Map<String,dynamic>> messages, {List<Map<String,dynamic>>? attachments}) async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) throw Exception('EVREN API anahtarı ayarlanmamış. Ayarlar bölümünden ekleyin.');
    final content = <Map<String,dynamic>>[];
    final last = messages.last;
    final text = last['content']?.toString() ?? '';
    if (attachments != null && attachments.isNotEmpty) {
      content.add({'type':'text','text':text});
      for (final a in attachments) {
        if (a['mime'] == 'image/jpeg' || a['mime'] == 'image/png' || a['mime'] == 'image/webp') {
          content.add({'type':'image_url','image_url':{'url':'data:${a['mime']};base64,${a['base64']}'}});
        } else {
          content.add({'type':'text','text':'Ek dosya: ${a['name']}\n${a['extracted'] ?? 'Dosya içeriği metne dönüştürülemedi.'}'});
        }
      }
    }
    final payloadMessages = messages.map((m) {
      if (identical(m, last) && attachments != null && attachments.isNotEmpty) return {'role':m['role'], 'content':content};
      return {'role':m['role'], 'content':m['content']};
    }).toList();
    final d = Dio(BaseOptions(baseUrl: '${evrenBaseUrl.replaceAll(RegExp(r'/+
    final response = await d.post('chat/completions',
      data:{'model':evrenModel, 'messages':payloadMessages, 'stream':false},
      options:Options(headers:{'Authorization':'Bearer $key','X-API-Key':key,'Content-Type':'application/json'}));
    final choices = response.data['choices'] as List?;
    if (choices == null || choices.isEmpty) throw Exception('EVREN boş yanıt döndürdü.');
    final answer = choices.first['message']?['content'];
    if (answer is String) return answer;
    if (answer is List) return answer.map((e) => e['text'] ?? '').join('\n');
    return answer?.toString() ?? 'Boş yanıt.';
  }
  Future<void> saveToken(String token) => _storage.write(key: 'jarvis_token', value: token);
  Future<void> logout() => _storage.delete(key: 'jarvis_token');
  Future<bool> get hasToken async => (await _storage.read(key: 'jarvis_token'))?.isNotEmpty ?? false;
  Future<Map<String, dynamic>> login(String email, String password) async {
    final response = await _dio.post('/v1/auth/login', data: {'email': email, 'password': password});
    final token = response.data['token'] as String;
    await saveToken(token);
    return Map<String, dynamic>.from(response.data);
  }
  Future<Map<String, dynamic>> get(String path) async => Map<String, dynamic>.from((await _dio.get(path)).data);
  Future<List<dynamic>> getList(String path) async => List<dynamic>.from((await _dio.get(path)).data);
  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.post(path, data: data)).data);
  Future<Map<String, dynamic>> put(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.put(path, data: data)).data);
  Future<void> delete(String path) async { await _dio.delete(path); }
}
), '');
    evrenModel = model.trim();
    await _storage.write(key: 'evren_base_url', value: evrenBaseUrl);
    await _storage.write(key: 'evren_api_key', value: apiKey.trim());
    await _storage.write(key: 'evren_model', value: evrenModel);
  }
  Future<String?> readEvrenKey() => _storage.read(key: 'evren_api_key');
  Future<void> clearEvrenKey() => _storage.delete(key: 'evren_api_key');
  Future<String> testEvren() async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) throw Exception('Önce EVREN API anahtarını kaydedin.');
    final d = Dio(BaseOptions(baseUrl: evrenBaseUrl, connectTimeout: const Duration(seconds: 15), receiveTimeout: const Duration(seconds: 30)));
    final response = await d.get('/models', options: Options(headers: {'Authorization': 'Bearer $key'}));
    final data = response.data;
    if (data is Map && data['data'] is List) return (data['data'] as List).map((e) => e is Map ? e['id'].toString() : '').where((e) => e.isNotEmpty).take(30).join('\n');
    return data.toString();
  }
  Future<String> askEvren(List<Map<String,dynamic>> messages, {List<Map<String,dynamic>>? attachments}) async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) throw Exception('EVREN API anahtarı ayarlanmamış. Ayarlar bölümünden ekleyin.');
    final content = <Map<String,dynamic>>[];
    final last = messages.last;
    final text = last['content']?.toString() ?? '';
    if (attachments != null && attachments.isNotEmpty) {
      content.add({'type':'text','text':text});
      for (final a in attachments) {
        if (a['mime'] == 'image/jpeg' || a['mime'] == 'image/png' || a['mime'] == 'image/webp') {
          content.add({'type':'image_url','image_url':{'url':'data:${a['mime']};base64,${a['base64']}'}});
        } else {
          content.add({'type':'text','text':'Ek dosya: ${a['name']}\n${a['extracted'] ?? 'Dosya içeriği metne dönüştürülemedi.'}'});
        }
      }
    }
    final payloadMessages = messages.map((m) {
      if (identical(m, last) && attachments != null && attachments.isNotEmpty) return {'role':m['role'], 'content':content};
      return {'role':m['role'], 'content':m['content']};
    }).toList();
    final d = Dio(BaseOptions(baseUrl: evrenBaseUrl, connectTimeout: const Duration(seconds:20), receiveTimeout: const Duration(minutes:3)));
    final response = await d.post('/chat/completions',
      data:{'model':evrenModel, 'messages':payloadMessages, 'stream':false},
      options:Options(headers:{'Authorization':'Bearer $key','Content-Type':'application/json'}));
    final choices = response.data['choices'] as List?;
    if (choices == null || choices.isEmpty) throw Exception('EVREN boş yanıt döndürdü.');
    final answer = choices.first['message']?['content'];
    if (answer is String) return answer;
    if (answer is List) return answer.map((e) => e['text'] ?? '').join('\n');
    return answer?.toString() ?? 'Boş yanıt.';
  }
  Future<void> saveToken(String token) => _storage.write(key: 'jarvis_token', value: token);
  Future<void> logout() => _storage.delete(key: 'jarvis_token');
  Future<bool> get hasToken async => (await _storage.read(key: 'jarvis_token'))?.isNotEmpty ?? false;
  Future<Map<String, dynamic>> login(String email, String password) async {
    final response = await _dio.post('/v1/auth/login', data: {'email': email, 'password': password});
    final token = response.data['token'] as String;
    await saveToken(token);
    return Map<String, dynamic>.from(response.data);
  }
  Future<Map<String, dynamic>> get(String path) async => Map<String, dynamic>.from((await _dio.get(path)).data);
  Future<List<dynamic>> getList(String path) async => List<dynamic>.from((await _dio.get(path)).data);
  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.post(path, data: data)).data);
  Future<Map<String, dynamic>> put(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.put(path, data: data)).data);
  Future<void> delete(String path) async { await _dio.delete(path); }
}
), '')}/', connectTimeout: const Duration(seconds: 15), receiveTimeout: const Duration(seconds: 30)));
    final response = await d.get('/models', options: Options(headers: {'Authorization': 'Bearer $key'}));
    final data = response.data;
    if (data is Map && data['data'] is List) return (data['data'] as List).map((e) => e is Map ? e['id'].toString() : '').where((e) => e.isNotEmpty).take(30).join('\n');
    return data.toString();
  }
  Future<String> askEvren(List<Map<String,dynamic>> messages, {List<Map<String,dynamic>>? attachments}) async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) throw Exception('EVREN API anahtarı ayarlanmamış. Ayarlar bölümünden ekleyin.');
    final content = <Map<String,dynamic>>[];
    final last = messages.last;
    final text = last['content']?.toString() ?? '';
    if (attachments != null && attachments.isNotEmpty) {
      content.add({'type':'text','text':text});
      for (final a in attachments) {
        if (a['mime'] == 'image/jpeg' || a['mime'] == 'image/png' || a['mime'] == 'image/webp') {
          content.add({'type':'image_url','image_url':{'url':'data:${a['mime']};base64,${a['base64']}'}});
        } else {
          content.add({'type':'text','text':'Ek dosya: ${a['name']}\n${a['extracted'] ?? 'Dosya içeriği metne dönüştürülemedi.'}'});
        }
      }
    }
    final payloadMessages = messages.map((m) {
      if (identical(m, last) && attachments != null && attachments.isNotEmpty) return {'role':m['role'], 'content':content};
      return {'role':m['role'], 'content':m['content']};
    }).toList();
    final d = Dio(BaseOptions(baseUrl: evrenBaseUrl, connectTimeout: const Duration(seconds:20), receiveTimeout: const Duration(minutes:3)));
    final response = await d.post('/chat/completions',
      data:{'model':evrenModel, 'messages':payloadMessages, 'stream':false},
      options:Options(headers:{'Authorization':'Bearer $key','Content-Type':'application/json'}));
    final choices = response.data['choices'] as List?;
    if (choices == null || choices.isEmpty) throw Exception('EVREN boş yanıt döndürdü.');
    final answer = choices.first['message']?['content'];
    if (answer is String) return answer;
    if (answer is List) return answer.map((e) => e['text'] ?? '').join('\n');
    return answer?.toString() ?? 'Boş yanıt.';
  }
  Future<void> saveToken(String token) => _storage.write(key: 'jarvis_token', value: token);
  Future<void> logout() => _storage.delete(key: 'jarvis_token');
  Future<bool> get hasToken async => (await _storage.read(key: 'jarvis_token'))?.isNotEmpty ?? false;
  Future<Map<String, dynamic>> login(String email, String password) async {
    final response = await _dio.post('/v1/auth/login', data: {'email': email, 'password': password});
    final token = response.data['token'] as String;
    await saveToken(token);
    return Map<String, dynamic>.from(response.data);
  }
  Future<Map<String, dynamic>> get(String path) async => Map<String, dynamic>.from((await _dio.get(path)).data);
  Future<List<dynamic>> getList(String path) async => List<dynamic>.from((await _dio.get(path)).data);
  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.post(path, data: data)).data);
  Future<Map<String, dynamic>> put(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.put(path, data: data)).data);
  Future<void> delete(String path) async { await _dio.delete(path); }
}
), '');
    evrenModel = model.trim();
    await _storage.write(key: 'evren_base_url', value: evrenBaseUrl);
    await _storage.write(key: 'evren_api_key', value: apiKey.trim());
    await _storage.write(key: 'evren_model', value: evrenModel);
  }
  Future<String?> readEvrenKey() => _storage.read(key: 'evren_api_key');
  Future<void> clearEvrenKey() => _storage.delete(key: 'evren_api_key');
  Future<String> testEvren() async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) throw Exception('Önce EVREN API anahtarını kaydedin.');
    final d = Dio(BaseOptions(baseUrl: evrenBaseUrl, connectTimeout: const Duration(seconds: 15), receiveTimeout: const Duration(seconds: 30)));
    final response = await d.get('/models', options: Options(headers: {'Authorization': 'Bearer $key'}));
    final data = response.data;
    if (data is Map && data['data'] is List) return (data['data'] as List).map((e) => e is Map ? e['id'].toString() : '').where((e) => e.isNotEmpty).take(30).join('\n');
    return data.toString();
  }
  Future<String> askEvren(List<Map<String,dynamic>> messages, {List<Map<String,dynamic>>? attachments}) async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) throw Exception('EVREN API anahtarı ayarlanmamış. Ayarlar bölümünden ekleyin.');
    final content = <Map<String,dynamic>>[];
    final last = messages.last;
    final text = last['content']?.toString() ?? '';
    if (attachments != null && attachments.isNotEmpty) {
      content.add({'type':'text','text':text});
      for (final a in attachments) {
        if (a['mime'] == 'image/jpeg' || a['mime'] == 'image/png' || a['mime'] == 'image/webp') {
          content.add({'type':'image_url','image_url':{'url':'data:${a['mime']};base64,${a['base64']}'}});
        } else {
          content.add({'type':'text','text':'Ek dosya: ${a['name']}\n${a['extracted'] ?? 'Dosya içeriği metne dönüştürülemedi.'}'});
        }
      }
    }
    final payloadMessages = messages.map((m) {
      if (identical(m, last) && attachments != null && attachments.isNotEmpty) return {'role':m['role'], 'content':content};
      return {'role':m['role'], 'content':m['content']};
    }).toList();
    final d = Dio(BaseOptions(baseUrl: evrenBaseUrl, connectTimeout: const Duration(seconds:20), receiveTimeout: const Duration(minutes:3)));
    final response = await d.post('/chat/completions',
      data:{'model':evrenModel, 'messages':payloadMessages, 'stream':false},
      options:Options(headers:{'Authorization':'Bearer $key','Content-Type':'application/json'}));
    final choices = response.data['choices'] as List?;
    if (choices == null || choices.isEmpty) throw Exception('EVREN boş yanıt döndürdü.');
    final answer = choices.first['message']?['content'];
    if (answer is String) return answer;
    if (answer is List) return answer.map((e) => e['text'] ?? '').join('\n');
    return answer?.toString() ?? 'Boş yanıt.';
  }
  Future<void> saveToken(String token) => _storage.write(key: 'jarvis_token', value: token);
  Future<void> logout() => _storage.delete(key: 'jarvis_token');
  Future<bool> get hasToken async => (await _storage.read(key: 'jarvis_token'))?.isNotEmpty ?? false;
  Future<Map<String, dynamic>> login(String email, String password) async {
    final response = await _dio.post('/v1/auth/login', data: {'email': email, 'password': password});
    final token = response.data['token'] as String;
    await saveToken(token);
    return Map<String, dynamic>.from(response.data);
  }
  Future<Map<String, dynamic>> get(String path) async => Map<String, dynamic>.from((await _dio.get(path)).data);
  Future<List<dynamic>> getList(String path) async => List<dynamic>.from((await _dio.get(path)).data);
  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.post(path, data: data)).data);
  Future<Map<String, dynamic>> put(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.put(path, data: data)).data);
  Future<void> delete(String path) async { await _dio.delete(path); }
}
), '')}/', connectTimeout: const Duration(seconds:20), receiveTimeout: const Duration(minutes:3)));
    final response = await d.post('/chat/completions',
      data:{'model':evrenModel, 'messages':payloadMessages, 'stream':false},
      options:Options(headers:{'Authorization':'Bearer $key','Content-Type':'application/json'}));
    final choices = response.data['choices'] as List?;
    if (choices == null || choices.isEmpty) throw Exception('EVREN boş yanıt döndürdü.');
    final answer = choices.first['message']?['content'];
    if (answer is String) return answer;
    if (answer is List) return answer.map((e) => e['text'] ?? '').join('\n');
    return answer?.toString() ?? 'Boş yanıt.';
  }
  Future<void> saveToken(String token) => _storage.write(key: 'jarvis_token', value: token);
  Future<void> logout() => _storage.delete(key: 'jarvis_token');
  Future<bool> get hasToken async => (await _storage.read(key: 'jarvis_token'))?.isNotEmpty ?? false;
  Future<Map<String, dynamic>> login(String email, String password) async {
    final response = await _dio.post('/v1/auth/login', data: {'email': email, 'password': password});
    final token = response.data['token'] as String;
    await saveToken(token);
    return Map<String, dynamic>.from(response.data);
  }
  Future<Map<String, dynamic>> get(String path) async => Map<String, dynamic>.from((await _dio.get(path)).data);
  Future<List<dynamic>> getList(String path) async => List<dynamic>.from((await _dio.get(path)).data);
  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.post(path, data: data)).data);
  Future<Map<String, dynamic>> put(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.put(path, data: data)).data);
  Future<void> delete(String path) async { await _dio.delete(path); }
}
), '');
    evrenModel = model.trim();
    await _storage.write(key: 'evren_base_url', value: evrenBaseUrl);
    await _storage.write(key: 'evren_api_key', value: apiKey.trim());
    await _storage.write(key: 'evren_model', value: evrenModel);
  }
  Future<String?> readEvrenKey() => _storage.read(key: 'evren_api_key');
  Future<void> clearEvrenKey() => _storage.delete(key: 'evren_api_key');
  Future<String> testEvren() async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) throw Exception('Önce EVREN API anahtarını kaydedin.');
    final d = Dio(BaseOptions(baseUrl: evrenBaseUrl, connectTimeout: const Duration(seconds: 15), receiveTimeout: const Duration(seconds: 30)));
    final response = await d.get('/models', options: Options(headers: {'Authorization': 'Bearer $key'}));
    final data = response.data;
    if (data is Map && data['data'] is List) return (data['data'] as List).map((e) => e is Map ? e['id'].toString() : '').where((e) => e.isNotEmpty).take(30).join('\n');
    return data.toString();
  }
  Future<String> askEvren(List<Map<String,dynamic>> messages, {List<Map<String,dynamic>>? attachments}) async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) throw Exception('EVREN API anahtarı ayarlanmamış. Ayarlar bölümünden ekleyin.');
    final content = <Map<String,dynamic>>[];
    final last = messages.last;
    final text = last['content']?.toString() ?? '';
    if (attachments != null && attachments.isNotEmpty) {
      content.add({'type':'text','text':text});
      for (final a in attachments) {
        if (a['mime'] == 'image/jpeg' || a['mime'] == 'image/png' || a['mime'] == 'image/webp') {
          content.add({'type':'image_url','image_url':{'url':'data:${a['mime']};base64,${a['base64']}'}});
        } else {
          content.add({'type':'text','text':'Ek dosya: ${a['name']}\n${a['extracted'] ?? 'Dosya içeriği metne dönüştürülemedi.'}'});
        }
      }
    }
    final payloadMessages = messages.map((m) {
      if (identical(m, last) && attachments != null && attachments.isNotEmpty) return {'role':m['role'], 'content':content};
      return {'role':m['role'], 'content':m['content']};
    }).toList();
    final d = Dio(BaseOptions(baseUrl: evrenBaseUrl, connectTimeout: const Duration(seconds:20), receiveTimeout: const Duration(minutes:3)));
    final response = await d.post('/chat/completions',
      data:{'model':evrenModel, 'messages':payloadMessages, 'stream':false},
      options:Options(headers:{'Authorization':'Bearer $key','Content-Type':'application/json'}));
    final choices = response.data['choices'] as List?;
    if (choices == null || choices.isEmpty) throw Exception('EVREN boş yanıt döndürdü.');
    final answer = choices.first['message']?['content'];
    if (answer is String) return answer;
    if (answer is List) return answer.map((e) => e['text'] ?? '').join('\n');
    return answer?.toString() ?? 'Boş yanıt.';
  }
  Future<void> saveToken(String token) => _storage.write(key: 'jarvis_token', value: token);
  Future<void> logout() => _storage.delete(key: 'jarvis_token');
  Future<bool> get hasToken async => (await _storage.read(key: 'jarvis_token'))?.isNotEmpty ?? false;
  Future<Map<String, dynamic>> login(String email, String password) async {
    final response = await _dio.post('/v1/auth/login', data: {'email': email, 'password': password});
    final token = response.data['token'] as String;
    await saveToken(token);
    return Map<String, dynamic>.from(response.data);
  }
  Future<Map<String, dynamic>> get(String path) async => Map<String, dynamic>.from((await _dio.get(path)).data);
  Future<List<dynamic>> getList(String path) async => List<dynamic>.from((await _dio.get(path)).data);
  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.post(path, data: data)).data);
  Future<Map<String, dynamic>> put(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.put(path, data: data)).data);
  Future<void> delete(String path) async { await _dio.delete(path); }
}
), '')}/', connectTimeout: const Duration(seconds: 15), receiveTimeout: const Duration(seconds: 30)));
    final response = await d.get('/models', options: Options(headers: {'Authorization': 'Bearer $key'}));
    final data = response.data;
    if (data is Map && data['data'] is List) return (data['data'] as List).map((e) => e is Map ? e['id'].toString() : '').where((e) => e.isNotEmpty).take(30).join('\n');
    return data.toString();
  }
  Future<String> askEvren(List<Map<String,dynamic>> messages, {List<Map<String,dynamic>>? attachments}) async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) throw Exception('EVREN API anahtarı ayarlanmamış. Ayarlar bölümünden ekleyin.');
    final content = <Map<String,dynamic>>[];
    final last = messages.last;
    final text = last['content']?.toString() ?? '';
    if (attachments != null && attachments.isNotEmpty) {
      content.add({'type':'text','text':text});
      for (final a in attachments) {
        if (a['mime'] == 'image/jpeg' || a['mime'] == 'image/png' || a['mime'] == 'image/webp') {
          content.add({'type':'image_url','image_url':{'url':'data:${a['mime']};base64,${a['base64']}'}});
        } else {
          content.add({'type':'text','text':'Ek dosya: ${a['name']}\n${a['extracted'] ?? 'Dosya içeriği metne dönüştürülemedi.'}'});
        }
      }
    }
    final payloadMessages = messages.map((m) {
      if (identical(m, last) && attachments != null && attachments.isNotEmpty) return {'role':m['role'], 'content':content};
      return {'role':m['role'], 'content':m['content']};
    }).toList();
    final d = Dio(BaseOptions(baseUrl: evrenBaseUrl, connectTimeout: const Duration(seconds:20), receiveTimeout: const Duration(minutes:3)));
    final response = await d.post('/chat/completions',
      data:{'model':evrenModel, 'messages':payloadMessages, 'stream':false},
      options:Options(headers:{'Authorization':'Bearer $key','Content-Type':'application/json'}));
    final choices = response.data['choices'] as List?;
    if (choices == null || choices.isEmpty) throw Exception('EVREN boş yanıt döndürdü.');
    final answer = choices.first['message']?['content'];
    if (answer is String) return answer;
    if (answer is List) return answer.map((e) => e['text'] ?? '').join('\n');
    return answer?.toString() ?? 'Boş yanıt.';
  }
  Future<void> saveToken(String token) => _storage.write(key: 'jarvis_token', value: token);
  Future<void> logout() => _storage.delete(key: 'jarvis_token');
  Future<bool> get hasToken async => (await _storage.read(key: 'jarvis_token'))?.isNotEmpty ?? false;
  Future<Map<String, dynamic>> login(String email, String password) async {
    final response = await _dio.post('/v1/auth/login', data: {'email': email, 'password': password});
    final token = response.data['token'] as String;
    await saveToken(token);
    return Map<String, dynamic>.from(response.data);
  }
  Future<Map<String, dynamic>> get(String path) async => Map<String, dynamic>.from((await _dio.get(path)).data);
  Future<List<dynamic>> getList(String path) async => List<dynamic>.from((await _dio.get(path)).data);
  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.post(path, data: data)).data);
  Future<Map<String, dynamic>> put(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.put(path, data: data)).data);
  Future<void> delete(String path) async { await _dio.delete(path); }
}
), '');
    evrenModel = model.trim();
    await _storage.write(key: 'evren_base_url', value: evrenBaseUrl);
    await _storage.write(key: 'evren_api_key', value: apiKey.trim());
    await _storage.write(key: 'evren_model', value: evrenModel);
  }
  Future<String?> readEvrenKey() => _storage.read(key: 'evren_api_key');
  Future<void> clearEvrenKey() => _storage.delete(key: 'evren_api_key');
  Future<String> testEvren() async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) throw Exception('Önce EVREN API anahtarını kaydedin.');
    final d = Dio(BaseOptions(baseUrl: evrenBaseUrl, connectTimeout: const Duration(seconds: 15), receiveTimeout: const Duration(seconds: 30)));
    final response = await d.get('/models', options: Options(headers: {'Authorization': 'Bearer $key'}));
    final data = response.data;
    if (data is Map && data['data'] is List) return (data['data'] as List).map((e) => e is Map ? e['id'].toString() : '').where((e) => e.isNotEmpty).take(30).join('\n');
    return data.toString();
  }
  Future<String> askEvren(List<Map<String,dynamic>> messages, {List<Map<String,dynamic>>? attachments}) async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) throw Exception('EVREN API anahtarı ayarlanmamış. Ayarlar bölümünden ekleyin.');
    final content = <Map<String,dynamic>>[];
    final last = messages.last;
    final text = last['content']?.toString() ?? '';
    if (attachments != null && attachments.isNotEmpty) {
      content.add({'type':'text','text':text});
      for (final a in attachments) {
        if (a['mime'] == 'image/jpeg' || a['mime'] == 'image/png' || a['mime'] == 'image/webp') {
          content.add({'type':'image_url','image_url':{'url':'data:${a['mime']};base64,${a['base64']}'}});
        } else {
          content.add({'type':'text','text':'Ek dosya: ${a['name']}\n${a['extracted'] ?? 'Dosya içeriği metne dönüştürülemedi.'}'});
        }
      }
    }
    final payloadMessages = messages.map((m) {
      if (identical(m, last) && attachments != null && attachments.isNotEmpty) return {'role':m['role'], 'content':content};
      return {'role':m['role'], 'content':m['content']};
    }).toList();
    final d = Dio(BaseOptions(baseUrl: evrenBaseUrl, connectTimeout: const Duration(seconds:20), receiveTimeout: const Duration(minutes:3)));
    final response = await d.post('/chat/completions',
      data:{'model':evrenModel, 'messages':payloadMessages, 'stream':false},
      options:Options(headers:{'Authorization':'Bearer $key','Content-Type':'application/json'}));
    final choices = response.data['choices'] as List?;
    if (choices == null || choices.isEmpty) throw Exception('EVREN boş yanıt döndürdü.');
    final answer = choices.first['message']?['content'];
    if (answer is String) return answer;
    if (answer is List) return answer.map((e) => e['text'] ?? '').join('\n');
    return answer?.toString() ?? 'Boş yanıt.';
  }
  Future<void> saveToken(String token) => _storage.write(key: 'jarvis_token', value: token);
  Future<void> logout() => _storage.delete(key: 'jarvis_token');
  Future<bool> get hasToken async => (await _storage.read(key: 'jarvis_token'))?.isNotEmpty ?? false;
  Future<Map<String, dynamic>> login(String email, String password) async {
    final response = await _dio.post('/v1/auth/login', data: {'email': email, 'password': password});
    final token = response.data['token'] as String;
    await saveToken(token);
    return Map<String, dynamic>.from(response.data);
  }
  Future<Map<String, dynamic>> get(String path) async => Map<String, dynamic>.from((await _dio.get(path)).data);
  Future<List<dynamic>> getList(String path) async => List<dynamic>.from((await _dio.get(path)).data);
  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.post(path, data: data)).data);
  Future<Map<String, dynamic>> put(String path, Map<String, dynamic> data) async => Map<String, dynamic>.from((await _dio.put(path, data: data)).data);
  Future<void> delete(String path) async { await _dio.delete(path); }
}

