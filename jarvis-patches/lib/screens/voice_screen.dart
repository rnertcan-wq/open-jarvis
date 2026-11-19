import 'package:flutter/material.dart';
import 'package:speech_to_text/speech_to_text.dart' as stt;
import 'package:flutter_tts/flutter_tts.dart';
import '../services/api_client.dart';
import '../theme/app_theme.dart';

class VoiceScreen extends StatefulWidget {
  const VoiceScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<VoiceScreen> createState() => _VoiceScreenState();
}

class _VoiceScreenState extends State<VoiceScreen> {
  final speech = stt.SpeechToText();
  final tts = FlutterTts();
  bool available = false;
  bool listening = false;
  bool busy = false;
  String transcript = 'Konuşmak için mikrofon düğmesine dokun.';
  String answer = '';

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    available = await speech.initialize(
      onStatus: (status) {
        if (mounted) setState(() => listening = status == 'listening');
      },
      onError: (error) {
        if (mounted) {
          setState(() {
            listening = false;
            answer = 'Mikrofon/konuşma tanıma hatası: ${error.errorMsg}';
          });
        }
      },
    );
    await tts.setLanguage('tr-TR');
    await tts.setSpeechRate(0.48);
    if (mounted) setState(() {});
  }

  Future<void> toggle() async {
    if (listening) {
      await speech.stop();
      return;
    }
    if (!available) {
      setState(() => transcript = 'Konuşma tanıma kullanılamıyor. Android mikrofon ve konuşma tanıma izinlerini kontrol edin.');
      return;
    }
    await speech.listen(
      localeId: 'tr_TR',
      onResult: (result) {
        if (mounted) setState(() => transcript = result.recognizedWords);
        if (result.finalResult) _ask(result.recognizedWords);
      },
    );
  }

  Future<void> _ask(String text) async {
    if (text.trim().isEmpty || busy) return;
    setState(() {
      busy = true;
      answer = '';
    });
    try {
      final response = await widget.api.askEvren([
        {'role': 'system', 'content': 'Sen JARVIS adlı Türkçe konuşan, doğal, kısa ve yardımcı bir sesli asistansın. Yanıtlarını konuşmaya uygun, akıcı Türkçe ver. Gereksiz markdown, tablo ve uzun madde listeleri kullanma.'},
        {'role': 'user', 'content': text.trim()},
      ]);
      if (!mounted) return;
      setState(() => answer = response);
      await tts.speak(response);
    } catch (e) {
      if (mounted) setState(() => answer = 'EVREN bağlantı hatası: $e');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  void dispose() {
    speech.stop();
    tts.stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(children: [
            const Text('Sesli asistan',
                style: TextStyle(fontSize: 25, fontWeight: FontWeight.w800)),
            const SizedBox(height: 8),
            const Text(
              'JARVIS ile konuş. Konuşma tanıma ve seslendirme cihazda, yapay zekâ yanıtları EVREN API üzerinden çalışır.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.white54, height: 1.5),
            ),
            const SizedBox(height: 42),
            GestureDetector(
              onTap: busy ? null : toggle,
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 250),
                width: 154,
                height: 154,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: listening ? const Color(0xFF382A64) : AppTheme.panel,
                  border: Border.all(
                    color: listening ? AppTheme.cyan : AppTheme.accent,
                    width: 2,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: AppTheme.accent.withOpacity(.18),
                      blurRadius: 35,
                      spreadRadius: 5,
                    )
                  ],
                ),
                child: Icon(
                  listening ? Icons.graphic_eq : Icons.mic_none_rounded,
                  size: 62,
                  color: AppTheme.cyan,
                ),
              ),
            ),
            const SizedBox(height: 24),
            Text(
              listening ? 'Dinliyorum…' : busy ? 'JARVIS yanıtlıyor…' : 'Dokun ve konuş',
              style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 17),
            ),
            const SizedBox(height: 22),
            _panel('Algılanan konuşma', transcript),
            if (answer.isNotEmpty) ...[
              const SizedBox(height: 12),
              _panel('JARVIS', answer),
            ],
          ]),
        ),
      );

  Widget _panel(String title, String text) => Container(
        width: double.infinity,
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(18),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title,
                style: const TextStyle(
                    color: AppTheme.cyan, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            SelectableText(text, style: const TextStyle(height: 1.45)),
          ],
        ),
      );
}
