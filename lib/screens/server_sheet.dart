import 'package:flutter/material.dart';
import '../config/app_config.dart';

class ServerSheet extends StatefulWidget {
  const ServerSheet({super.key});

  @override
  State<ServerSheet> createState() => _ServerSheetState();
}

class _ServerSheetState extends State<ServerSheet> {
  late TextEditingController _urlController;
  late TextEditingController _keyController;
  late int _maxConcurrent;

  @override
  void initState() {
    super.initState();
    _urlController = TextEditingController(text: AppConfig.serverUrl);
    _keyController = TextEditingController(text: AppConfig.apiKey);
    _maxConcurrent = AppConfig.maxConcurrentDownloads;
  }

  @override
  void dispose() {
    _urlController.dispose();
    _keyController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    await AppConfig.saveServerUrl(_urlController.text);
    await AppConfig.saveApiKey(_keyController.text);
    await AppConfig.saveMaxConcurrent(_maxConcurrent);

    if (mounted) {
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text("Server settings updated"),
          backgroundColor: Color(0xFF1E293B),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
        left: 20,
        right: 20,
        top: 20,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                "Server Configuration",
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
              ),
              IconButton(
                icon: const Icon(Icons.close, color: Colors.white60),
                onPressed: () => Navigator.pop(context),
              ),
            ],
          ),
          const SizedBox(height: 16),
          const Text("Server Endpoint URL", style: TextStyle(color: Colors.white70, fontSize: 13)),
          const SizedBox(height: 6),
          TextField(
            controller: _urlController,
            style: const TextStyle(color: Colors.white, fontSize: 14),
            decoration: InputDecoration(
              filled: true,
              fillColor: const Color(0xFF161922),
              hintText: "https://your-ngrok-url.dev",
              hintStyle: const TextStyle(color: Colors.white30),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
              contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            ),
          ),
          const SizedBox(height: 16),
          const Text("API Key", style: TextStyle(color: Colors.white70, fontSize: 13)),
          const SizedBox(height: 6),
          TextField(
            controller: _keyController,
            style: const TextStyle(color: Colors.white, fontSize: 14),
            decoration: InputDecoration(
              filled: true,
              fillColor: const Color(0xFF161922),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
              contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text("Max Concurrent Downloads", style: TextStyle(color: Colors.white70, fontSize: 13)),
              DropdownButton<int>(
                value: _maxConcurrent,
                dropdownColor: const Color(0xFF1E293B),
                style: const TextStyle(color: Color(0xFF38BDF8), fontWeight: FontWeight.bold),
                items: [1, 2, 3, 4, 5].map((val) {
                  return DropdownMenuItem<int>(
                    value: val,
                    child: Text("$val active"),
                  );
                }).toList(),
                onChanged: (val) {
                  if (val != null) setState(() => _maxConcurrent = val);
                },
              ),
            ],
          ),
          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF0284C7),
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
              onPressed: _save,
              child: const Text("Save & Apply", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
            ),
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }
}
