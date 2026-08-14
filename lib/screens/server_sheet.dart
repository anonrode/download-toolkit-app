import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import '../config/app_config.dart';

class ServerSheet extends StatefulWidget {
  const ServerSheet({super.key});

  @override
  State<ServerSheet> createState() => _ServerSheetState();
}

class _ServerSheetState extends State<ServerSheet> {
  late TextEditingController _urlController;
  late TextEditingController _keyController;
  String _quality = AppConfig.defaultQuality;
  bool _instantSocial = AppConfig.instantSocialDownload;
  bool _isTesting = false;
  String? _testResult;
  bool _testSuccess = false;

  @override
  void initState() {
    super.initState();
    _urlController = TextEditingController(text: AppConfig.serverUrl);
    _keyController = TextEditingController(text: AppConfig.apiKey);
  }

  @override
  void dispose() {
    _urlController.dispose();
    _keyController.dispose();
    super.dispose();
  }

  Future<void> _testConnection() async {
    final testUrl = _urlController.text.trim().replaceAll(RegExp(r'/+$'), '');
    final testKey = _keyController.text.trim();

    setState(() {
      _isTesting = true;
      _testResult = null;
    });

    final stopwatch = Stopwatch()..start();
    try {
      final dio = Dio(BaseOptions(connectTimeout: const Duration(seconds: 10)));
      final res = await dio.get(
        "$testUrl/health",
        options: Options(headers: {
          'x-api-key': testKey,
          'ngrok-skip-browser-warning': '1',
        }),
      );
      stopwatch.stop();

      if (res.statusCode == 200) {
        setState(() {
          _testSuccess = true;
          _testResult = "Connected! (${stopwatch.elapsedMilliseconds}ms latency)";
          _isTesting = false;
        });
      } else {
        setState(() {
          _testSuccess = false;
          _testResult = "Server error (Status: ${res.statusCode})";
          _isTesting = false;
        });
      }
    } catch (e) {
      stopwatch.stop();
      setState(() {
        _testSuccess = false;
        _testResult = "Connection failed: $e";
        _isTesting = false;
      });
    }
  }

  Future<void> _saveSettings() async {
    await AppConfig.saveServerUrl(_urlController.text);
    await AppConfig.saveApiKey(_keyController.text);
    await AppConfig.saveDefaultQuality(_quality);
    await AppConfig.saveInstantSocial(_instantSocial);

    if (mounted) {
      Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text("Settings saved"),
          backgroundColor: Color(0xFF141722),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
      decoration: const BoxDecoration(
        color: Color(0xFF141722),
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Drag Handle
            Center(
              child: Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.white24,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Header
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: const Color(0xFF00E5FF).withOpacity(0.12),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.tune_rounded, color: Color(0xFF00E5FF), size: 20),
                ),
                const SizedBox(width: 12),
                const Text(
                  "App & Server Settings",
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold, color: Colors.white),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Server URL
            const Text("Server Endpoint", style: TextStyle(color: Colors.white70, fontSize: 12, fontWeight: FontWeight.w600)),
            const SizedBox(height: 6),
            TextField(
              controller: _urlController,
              style: const TextStyle(color: Colors.white, fontSize: 13),
              decoration: InputDecoration(
                filled: true,
                fillColor: const Color(0xFF1E2333),
                contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
              ),
            ),
            const SizedBox(height: 14),

            // API Key
            const Text("Master API Key", style: TextStyle(color: Colors.white70, fontSize: 12, fontWeight: FontWeight.w600)),
            const SizedBox(height: 6),
            TextField(
              controller: _keyController,
              style: const TextStyle(color: Colors.white, fontSize: 13),
              decoration: InputDecoration(
                filled: true,
                fillColor: const Color(0xFF1E2333),
                contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
              ),
            ),
            const SizedBox(height: 14),

            // Quality Preference
            const Text("Default Video Quality", style: TextStyle(color: Colors.white70, fontSize: 12, fontWeight: FontWeight.w600)),
            const SizedBox(height: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              decoration: BoxDecoration(
                color: const Color(0xFF1E2333),
                borderRadius: BorderRadius.circular(14),
              ),
              child: DropdownButtonHideUnderline(
                child: DropdownButton<String>(
                  value: _quality,
                  dropdownColor: const Color(0xFF1E2333),
                  isExpanded: true,
                  style: const TextStyle(color: Colors.white, fontSize: 13),
                  items: const [
                    DropdownMenuItem(value: "best", child: Text("Best Available (1080p)")),
                    DropdownMenuItem(value: "720p", child: Text("720p HD (Recommended)")),
                    DropdownMenuItem(value: "480p", child: Text("480p (Data Saver)")),
                    DropdownMenuItem(value: "audio", child: Text("Audio Only (MP3)")),
                  ],
                  onChanged: (val) {
                    if (val != null) setState(() => _quality = val);
                  },
                ),
              ),
            ),
            const SizedBox(height: 14),

            // Instant Social Mode Toggle
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text("Instant Social Media Download", style: TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600)),
              subtitle: const Text("Skip preview sheet and download immediately when pasting links", style: TextStyle(color: Colors.white38, fontSize: 11)),
              value: _instantSocial,
              activeColor: const Color(0xFF00E5FF),
              onChanged: (val) => setState(() => _instantSocial = val),
            ),
            const SizedBox(height: 10),

            // Storage Folder Info
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.04),
                borderRadius: BorderRadius.circular(14),
              ),
              child: const Row(
                children: [
                  Icon(Icons.folder_outlined, color: Colors.white54, size: 18),
                  SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      "Storage: /storage/emulated/0/Download/Anon/",
                      style: TextStyle(color: Colors.white54, fontSize: 11),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 14),

            // Test Connection Button
            OutlinedButton(
              onPressed: _isTesting ? null : _testConnection,
              style: OutlinedButton.styleFrom(
                foregroundColor: const Color(0xFF00E5FF),
                side: BorderSide(color: const Color(0xFF00E5FF).withOpacity(0.5)),
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
              ),
              child: _isTesting
                  ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF00E5FF)))
                  : const Text("Test Server Connection", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
            ),

            if (_testResult != null) ...[
              const SizedBox(height: 8),
              Text(
                _testResult!,
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: _testSuccess ? const Color(0xFF10B981) : Colors.redAccent,
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
            const SizedBox(height: 16),

            // Save Button
            ElevatedButton(
              onPressed: _saveSettings,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF00E5FF),
                foregroundColor: Colors.black,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                elevation: 0,
              ),
              child: const Text("Save & Apply", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
            ),
          ],
        ),
      ),
    );
  }
}
