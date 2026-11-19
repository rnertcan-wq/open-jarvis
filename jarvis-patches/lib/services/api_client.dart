import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class ApiClient {
  ApiClient()
      : _dio = Dio(BaseOptions(
          connectTimeout: const Duration(seconds: 20),
          receiveTimeout: const Duration(minutes: 3),
        )) {
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final token = await _storage.read(key: 'jarvis_token');
        if (token != null && token.isNotEmpty) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
    ));
  }

  final Dio _dio;
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  String _baseUrl = 'http://10.0.2.2:8080';
  String evrenBaseUrl = 'https://evren-llmapi.ssyz.org.tr/v1';
  String evrenModel = 'auto';

  String get baseUrl => _baseUrl;

  Future<void> initialize() async {
    _baseUrl = await _storage.read(key: 'jarvis_base_url') ?? _baseUrl;
    evrenBaseUrl = await _storage.read(key: 'evren_base_url') ?? evrenBaseUrl;
    evrenModel = await _storage.read(key: 'evren_model') ?? evrenModel;
    _dio.options.baseUrl = _baseUrl;
  }

  Future<void> setBaseUrl(String value) async {
    _baseUrl = value.trim().replaceAll(RegExp(r'/+$'), '');
    _dio.options.baseUrl = _baseUrl;
    await _storage.write(key: 'jarvis_base_url', value: _baseUrl);
  }

  Future<void> saveEvren({
    required String baseUrl,
    required String apiKey,
    required String model,
  }) async {
    evrenBaseUrl = baseUrl.trim().replaceAll(RegExp(r'/+$'), '');
    evrenModel = model.trim().isEmpty ? 'auto' : model.trim();
    await _storage.write(key: 'evren_base_url', value: evrenBaseUrl);
    await _storage.write(key: 'evren_api_key', value: apiKey.trim());
    await _storage.write(key: 'evren_model', value: evrenModel);
  }

  Future<String?> readEvrenKey() => _storage.read(key: 'evren_api_key');

  Future<void> clearEvrenKey() => _storage.delete(key: 'evren_api_key');

  Future<Map<String, dynamic>> evrenTermsStatus() async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) {
      throw Exception('Önce EVREN API anahtarını kaydedin.');
    }
    final d = Dio(BaseOptions(baseUrl: '${evrenBaseUrl.replaceAll(RegExp(r'/+$'), '')}/'));
    final response = await d.get('terms/status',
        options: Options(headers: {'X-API-Key': key, 'Authorization': 'Bearer $key'}));
    return Map<String, dynamic>.from(response.data as Map);
  }

  Future<String> evrenTermsText() async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) {
      throw Exception('Önce EVREN API anahtarını kaydedin.');
    }
    final d = Dio(BaseOptions(baseUrl: '${evrenBaseUrl.replaceAll(RegExp(r'/+$'), '')}/'));
    final response = await d.get('terms/text',
        options: Options(headers: {'X-API-Key': key, 'Authorization': 'Bearer $key'}));
    return (response.data as Map)['content']?.toString() ?? response.data.toString();
  }

  Future<void> acceptEvrenTerms(int version) async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) {
      throw Exception('Önce EVREN API anahtarını kaydedin.');
    }
    final d = Dio(BaseOptions(baseUrl: '${evrenBaseUrl.replaceAll(RegExp(r'/+$'), '')}/'));
    await d.post('terms/accept',
        data: {'version': version},
        options: Options(headers: {
          'X-API-Key': key,
          'Authorization': 'Bearer $key',
          'Content-Type': 'application/json',
        }));
  }

  Future<List<String>> testEvren() async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) {
      throw Exception('Önce EVREN API anahtarını kaydedin.');
    }
    final d = Dio(BaseOptions(
      baseUrl: '${evrenBaseUrl.replaceAll(RegExp(r'/+$'), '')}/',
      connectTimeout: const Duration(seconds: 20),
      receiveTimeout: const Duration(seconds: 30),
    ));
    final response = await d.get('models',
        options: Options(headers: {
          'X-API-Key': key,
          'Authorization': 'Bearer $key',
        }));
    final data = response.data;
    if (data is Map && data['data'] is List) {
      return (data['data'] as List)
          .whereType<Map>()
          .map((item) => item['id']?.toString() ?? '')
          .where((id) => id.isNotEmpty)
          .toList();
    }
    throw Exception('EVREN model listesi beklenen biçimde dönmedi.');
  }

  Future<String> askEvren(
    List<Map<String, dynamic>> messages, {
    List<Map<String, dynamic>>? attachments,
  }) async {
    final key = await readEvrenKey();
    if (key == null || key.isEmpty) {
      throw Exception('EVREN API anahtarı ayarlanmamış. Ayarlar bölümünden ekleyin.');
    }
    if (messages.isEmpty) throw Exception('Gönderilecek mesaj bulunamadı.');

    final last = messages.last;
    final content = <Map<String, dynamic>>[];
    final text = last['content']?.toString() ?? '';

    if (attachments != null && attachments.isNotEmpty) {
      content.add({'type': 'text', 'text': text});
      for (final file in attachments) {
        final mime = file['mime']?.toString() ?? '';
        if (mime.startsWith('image/')) {
          content.add({
            'type': 'image_url',
            'image_url': {
              'url': 'data:$mime;base64,${file['base64']}',
            },
          });
        } else {
          content.add({
            'type': 'text',
            'text': 'Ek dosya: ${file['name']}\n${file['extracted'] ?? 'Dosya metni çıkarılamadı.'}',
          });
        }
      }
    }

    final payload = messages.asMap().entries.map((entry) {
      final message = entry.value;
      if (entry.key == messages.length - 1 &&
          attachments != null &&
          attachments.isNotEmpty) {
        return {'role': message['role'], 'content': content};
      }
      return {'role': message['role'], 'content': message['content']};
    }).toList();

    final d = Dio(BaseOptions(
      baseUrl: '${evrenBaseUrl.replaceAll(RegExp(r'/+$'), '')}/',
      connectTimeout: const Duration(seconds: 20),
      receiveTimeout: const Duration(minutes: 3),
    ));
    final response = await d.post(
      'chat/completions',
      data: {
        'model': evrenModel,
        'messages': payload,
        'max_tokens': 4096,
        'stream': false,
      },
      options: Options(headers: {
        'X-API-Key': key,
        'Authorization': 'Bearer $key',
        'Content-Type': 'application/json',
      }),
    );

    final choices = response.data is Map ? response.data['choices'] as List? : null;
    if (choices == null || choices.isEmpty) {
      throw Exception('EVREN boş yanıt döndürdü.');
    }
    final answer = choices.first['message']?['content'];
    if (answer is String) return answer;
    if (answer is List) {
      return answer.map((part) => part is Map ? (part['text'] ?? '') : '').join('\n');
    }
    return answer?.toString() ?? 'Boş yanıt.';
  }

  Future<void> saveToken(String token) => _storage.write(key: 'jarvis_token', value: token);
  Future<void> logout() => _storage.delete(key: 'jarvis_token');
  Future<bool> get hasToken async =>
      (await _storage.read(key: 'jarvis_token'))?.isNotEmpty ?? false;

  Future<Map<String, dynamic>> login(String email, String password) async {
    final response = await _dio.post('/v1/auth/login',
        data: {'email': email, 'password': password});
    final token = response.data['token'] as String;
    await saveToken(token);
    return Map<String, dynamic>.from(response.data);
  }

  Future<Map<String, dynamic>> get(String path) async =>
      Map<String, dynamic>.from((await _dio.get(path)).data);
  Future<List<dynamic>> getList(String path) async =>
      List<dynamic>.from((await _dio.get(path)).data);
  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> data) async =>
      Map<String, dynamic>.from((await _dio.post(path, data: data)).data);
  Future<Map<String, dynamic>> put(String path, Map<String, dynamic> data) async =>
      Map<String, dynamic>.from((await _dio.put(path, data: data)).data);
  Future<void> delete(String path) async {
    await _dio.delete(path);
  }
}
