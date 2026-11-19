import 'package:flutter/material.dart';
import '../services/api_client.dart';
import '../theme/app_theme.dart';

class AgentsScreen extends StatefulWidget {
  const AgentsScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<AgentsScreen> createState() => _AgentsScreenState();
}

class _AgentsScreenState extends State<AgentsScreen> {
  final task = TextEditingController();
  bool loading = false;
  String? result;
  String? error;
  int completed = 0;

  static const team = [
    {
      'name': 'Araştırmacı ajan',
      'role': 'Bilgi toplama ve kapsam',
      'description': 'Görevi analiz eder, gerekli bilgileri ve belirsizlikleri çıkarır.',
      'instruction': 'Araştırmacı uzman gibi davran. İstenen görev için gerekli bilgileri, varsayımları, bilinmeyenleri ve doğrulanması gereken noktaları çıkar. Uydurma bilgi verme.'
    },
    {
      'name': 'Planlayıcı ajan',
      'role': 'Strateji ve uygulama',
      'description': 'Araştırma çıktısını uygulanabilir adımlara dönüştürür.',
      'instruction': 'Planlama uzmanı gibi davran. Önceki analizden hareketle uygulanabilir, sıralı ve ölçülebilir bir plan hazırla. Riskleri ve bağımlılıkları belirt.'
    },
    {
      'name': 'Denetçi ajan',
      'role': 'Eleştirel kontrol',
      'description': 'Eksikleri, çelişkileri ve riskleri denetler.',
      'instruction': 'Bağımsız denetçi gibi davran. Önceki çıktıyı eleştir; yanlış varsayımları, eksikleri, güvenlik ve uygulanabilirlik risklerini bul. Gereksiz övgü yapma.'
    },
  ];

  Future<String> _callAgent(String instruction, String prompt) {
    return widget.api.askEvren([
      {'role': 'system', 'content': instruction},
      {'role': 'user', 'content': prompt},
    ]);
  }

  Future<void> run() async {
    final request = task.text.trim();
    if (request.length < 3 || loading) return;
    setState(() {
      loading = true;
      result = null;
      error = null;
      completed = 0;
    });

    try {
      final research = await _callAgent(
        team[0]['instruction']!,
        'Görev: $request',
      );
      if (mounted) setState(() => completed = 1);

      final plan = await _callAgent(
        team[1]['instruction']!,
        'Kullanıcı görevi: $request\n\nAraştırmacı ajan çıktısı:\n$research',
      );
      if (mounted) setState(() => completed = 2);

      final audit = await _callAgent(
        team[2]['instruction']!,
        'Kullanıcı görevi: $request\n\nAraştırma:\n$research\n\nPlan:\n$plan',
      );
      if (mounted) setState(() => completed = 3);

      final finalAnswer = await _callAgent(
        'Sen JARVIS takım koordinatörüsün. Üç uzman çıktısını birleştir. Tekrarlananları çıkar, denetçinin haklı bulduğu riskleri plana işle. Türkçe, net, uygulanabilir ve dürüst bir nihai rapor üret. Araştırılmamış bilgiyi doğrulanmış gibi sunma.',
        'Kullanıcı görevi: $request\n\nARAŞTIRMACI:\n$research\n\nPLANLAYICI:\n$plan\n\nDENETÇİ:\n$audit\n\nBunları birleştirerek nihai sonucu hazırla.',
      );

      if (mounted) {
        setState(() {
          result = 'ARAŞTIRMACI AJAN\n$research\n\nPLANLAYICI AJAN\n$plan\n\nDENETÇİ AJAN\n$audit\n\nJARVIS SONUÇ\n$finalAnswer';
        });
      }
    } catch (e) {
      if (mounted) setState(() => error = 'Ajan görevi tamamlanamadı: $e');
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  void dispose() {
    task.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.fromLTRB(18, 20, 18, 24),
        children: [
          const Text('Ajan merkezi',
              style: TextStyle(fontSize: 25, fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          const Text(
            'Uzman ajanlar EVREN modeli üzerinden sırayla çalışır; sonuçlar JARVIS tarafından birleştirilir.',
            style: TextStyle(color: Colors.white54),
          ),
          const SizedBox(height: 18),
          ...team.map((agent) => Card(
                child: ListTile(
                  leading: CircleAvatar(
                    backgroundColor: AppTheme.panel,
                    child: const Icon(Icons.smart_toy_outlined,
                        color: AppTheme.cyan),
                  ),
                  title: Text(agent['name']!),
                  subtitle: Text(
                    '${agent['role']} · ${agent['description']}',
                    maxLines: 3,
                  ),
                  trailing: const Icon(Icons.check_circle_outline,
                      color: Colors.greenAccent),
                ),
              )),
          const SizedBox(height: 16),
          const Text('Yeni takım görevi',
              style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
          const SizedBox(height: 10),
          TextField(
            controller: task,
            minLines: 3,
            maxLines: 6,
            decoration: const InputDecoration(
              hintText: 'JARVIS, bu görevi uzman ajanlara dağıt…',
            ),
          ),
          const SizedBox(height: 10),
          FilledButton.icon(
            onPressed: loading ? null : run,
            icon: loading
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.rocket_launch),
            label: Text(loading
                ? 'Ajanlar çalışıyor… ($completed/4)'
                : 'Görevi başlat'),
          ),
          if (error != null)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(error!,
                  style: const TextStyle(color: Colors.redAccent)),
            ),
          if (result != null)
            Card(
              margin: const EdgeInsets.only(top: 16),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: SelectableText(result!,
                    style: const TextStyle(height: 1.5)),
              ),
            ),
        ],
      );
}
