import 'dart:io';
import 'package:shared_preferences/shared_preferences.dart';

class AppConfig {
  static const String defaultServerUrl = "http://68.155.146.145";
  static const String defaultApiKey = "4KOfpm9co8fVWVSS1sVjAA818zKdmLb";
  static const String clientVersion = "2.0.0";
  static const String defaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

  static String serverUrl = defaultServerUrl;
  static String apiKey = defaultApiKey;
  static int maxConcurrentDownloads = 2;
  static String defaultQuality = "720p"; // 'best', '1080p', '720p', 'audio'
  static bool instantSocialDownload = false; // true = instant, false = show preview sheet

  static const String _keyServerUrl = "server_url";
  static const String _keyApiKey = "api_key";
  static const String _keyMaxConcurrent = "max_concurrent";
  static const String _keyDefaultQuality = "default_quality";
  static const String _keyInstantSocial = "instant_social_download";

  static Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    serverUrl = prefs.getString(_keyServerUrl) ?? defaultServerUrl;
    apiKey = prefs.getString(_keyApiKey) ?? defaultApiKey;
    maxConcurrentDownloads = prefs.getInt(_keyMaxConcurrent) ?? 2;
    defaultQuality = prefs.getString(_keyDefaultQuality) ?? "720p";
    instantSocialDownload = prefs.getBool(_keyInstantSocial) ?? false;
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

  static Future<void> saveDefaultQuality(String quality) async {
    final prefs = await SharedPreferences.getInstance();
    defaultQuality = quality;
    await prefs.setString(_keyDefaultQuality, quality);
  }

  static Future<void> saveInstantSocial(bool instant) async {
    final prefs = await SharedPreferences.getInstance();
    instantSocialDownload = instant;
    await prefs.setBool(_keyInstantSocial, instant);
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
