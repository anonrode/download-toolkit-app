import 'dart:io';
import 'package:shared_preferences/shared_preferences.dart';

class AppConfig {
  static const String defaultServerUrl = "https://seldom-lyricist-triceps.ngrok-free.dev";
  static const String defaultApiKey = "anon_master_key";
  static const String clientVersion = "1.1.0";
  static const String defaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

  static String serverUrl = defaultServerUrl;
  static String apiKey = defaultApiKey;
  static int maxConcurrentDownloads = 2;

  static const String _keyServerUrl = "server_url";
  static const String _keyApiKey = "api_key";
  static const String _keyMaxConcurrent = "max_concurrent";

  static Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    serverUrl = prefs.getString(_keyServerUrl) ?? defaultServerUrl;
    apiKey = prefs.getString(_keyApiKey) ?? defaultApiKey;
    maxConcurrentDownloads = prefs.getInt(_keyMaxConcurrent) ?? 2;
  }

  static Future<void> saveServerUrl(String url) async {
    final prefs = await SharedPreferences.getInstance();
    serverUrl = url.trim().replaceAll(RegExp(r'/+$'), '');
    await prefs.setString(_keyServerUrl, serverUrl);
  }

  static Future<void> saveApiKey(String key) async {
    final prefs = await SharedPreferences.getInstance();
    apiKey = key.trim();
    await prefs.setString(_keyApiKey, apiKey);
  }

  static Future<void> saveMaxConcurrent(int max) async {
    final prefs = await SharedPreferences.getInstance();
    maxConcurrentDownloads = max;
    await prefs.setInt(_keyMaxConcurrent, max);
  }

  static Directory getAnonDownloadDirectory([String? showName]) {
    final baseDir = Directory('/storage/emulated/0/Download/Anon');
    if (showName != null && showName.isNotEmpty) {
      final sanitizedShow = showName.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_').trim();
      return Directory('${baseDir.path}/$sanitizedShow');
    }
    return baseDir;
  }
}
