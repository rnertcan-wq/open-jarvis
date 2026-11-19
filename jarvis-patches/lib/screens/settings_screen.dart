import 'package:flutter/material.dart';
import '../services/api_client.dart';
import '../theme/app_theme.dart';
import 'memory_screen.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final url = TextEditingController(text: 'https://evren-llmapi.ssyz.org.tr/v1');
  final key = TextEditingController();
  final model = TextEditingController(text: 'auto');
  bool busy = false;
  bool showKey = false;
  String status = 'EVREN API bilgilerinizi girip kaydedin.';
  List<String> models = [];

  @override
  void initState() {
    super.initState();
    url.text = widget.api.evrenBaseUrl;
    model.text = widget.api.evrenModel;
  }

  Future<void> save() async {
    if (key.text.trim().isEmpty) {
      setState(() => status = 'EVREN API anahtarını girin.');
      return;
    }
    setState(() => busy = true);
    try {
      await widget.api.saveEvren(
        baseUrl: url.text,
        apiKey: key.text,
        model: model.text,
      );
      key.clear();
      if (mounted) setState(() => status = 'API bilgileri telefonda güvenli şekilde kaydedildi.');
    } catch (e) {
      if (mounted) setState(() => status = 'Kaydetme hatası: $e');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> test() async {
    setState(() => busy = true);
    try {
      if (key.text.trim().isNotEmpty) {
        await widget.api.saveEvren(baseUrl: url.text, apiKey: key.text, model: model.text);
        key.clear();
      }
      final found = await widget.api.testEvren();
      if (mounted) {
        setState(() {
          models = found;
          status = 'Bağlantı başarılı. ${found.length} model bulundu.';
        });
      }
    } catch (e) {
      if (mounted) setState(() => status = 'Bağlantı testi: $e');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> showTerms() async {
    setState(() => busy = true);
    try {
      final termsStatus = await widget.api.evrenTermsStatus();
      final text = await widget.api.evrenTermsText();
      if (!mounted) return;
      final version = (termsStatus['current_version'] as num?)?.toInt() ?? 1;
      final accepted = termsStatus['accepted'] == true;
      await showDialog<void>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: const Text('EVREN kullanım şartları'),
          content: SizedBox(
            width: 500,
            child: SingleChildScrollView(
              child: Text(accepted ? 'Şartlar zaten kabul edilmiş.\n\n$text' : text),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('Kapat'),
            ),
            if (!accepted)
              FilledButton(
                onPressed: () async {
                  try {
                    await widget.api.acceptEvrenTerms(version);
                    if (dialogContext.mounted) Navigator.pop(dialogContext);
                    if (mounted) setState(() => status = 'EVREN kullanım şartları kabul edildi.');
                  } catch (e) {
                    if (mounted) setState(() => status = 'Şartlar kabul edilemedi: $e');
                  }
                },
                child: const Text('Okudum, kabul ediyorum'),
              ),
          ],
        ),
      );
    } catch (e) {
      if (mounted) setState(() => status = 'Şartlar alınamadı: $e');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  void dispose() {
    url.dispose();
    key.dispose();
    model.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(18),
        children: [
          const Text('Ayarlar', style: TextStyle(fontSize: 25, fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          const Text('EVREN API bağlantısı ve kişisel tercihler.',
              style: TextStyle(color: Colors.white54)),
          const SizedBox(height: 18),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('EVREN LLM API',
                    style: TextStyle(fontWeight: FontWeight.w800, fontSize: 17)),
                const SizedBox(height: 8),
                const Text(
                  'API anahtarı Android güvenli depolamasında saklanır. Kullanım şartlarını okuyup kabul etmeniz gerekebilir.',
                  style: TextStyle(color: Colors.white60, height: 1.4),
                ),
                const SizedBox(height: 14),
                TextField(
                  controller: url,
                  keyboardType: TextInputType.url,
                  decoration: const InputDecoration(labelText: 'Base URL'),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: key,
                  obscureText: !showKey,
                  decoration: InputDecoration(
                    labelText: 'EVREN API Key',
                    suffixIcon: IconButton(
                      onPressed: () => setState(() => showKey = !showKey),
                      icon: Icon(showKey ? Icons.visibility_off : Icons.visibility),
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: model,
                  decoration: const InputDecoration(labelText: 'Model ID', hintText: 'auto'),
                ),
                const SizedBox(height: 12),
                Wrap(spacing: 8, runSpacing: 8, children: [
                  OutlinedButton(
                    onPressed: busy ? null : save,
                    child: const Text('Kaydet'),
                  ),
                  OutlinedButton(
                    onPressed: busy ? null : test,
                    child: const Text('Bağlantıyı test et'),
                  ),
                  OutlinedButton(
                    onPressed: busy ? null : showTerms,
                    child: const Text('Kullanım şartları'),
                  ),
                ]),
                if (models.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  const Text('Hesabındaki modeller', style: TextStyle(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 6),
                  ...models.map((m) => ListTile(
                        dense: true,
                        contentPadding: EdgeInsets.zero,
                        title: Text(m),
                        trailing: IconButton(
                          icon: const Icon(Icons.check_circle_outline),
                          onPressed: () => setState(() {
                            model.text = m;
                            status = 'Seçilen model: $m';
                          }),
                        ),
                      )),
                ],
              ]),
            ),
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: AppTheme.panel,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Text(status,
                style: const TextStyle(color: Colors.white70, fontSize: 12)),
          ),
          const SizedBox(height: 14),
          Card(
            child: ListTile(
              leading: const Icon(Icons.psychology_alt_outlined, color: AppTheme.cyan),
              title: const Text('Kalıcı hafıza'),
              subtitle: const Text('Kayıtları görüntüle ve düzenle'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => MemoryScreen(api: widget.api)),
              ),
            ),
          ),
        ],
      );
}
