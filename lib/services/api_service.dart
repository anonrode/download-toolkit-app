import 'dart:convert';
import 'dart:io';
import 'package:dio/dio.dart';
import 'package:dio/io.dart';
import '../config/app_config.dart';
import '../models/show_model.dart';
import '../models/episode_model.dart';

class ApiService {
  static final Dio _dio = _createDio();

  static Dio _createDio() {
    final dio = Dio(
      BaseOptions(
        connectTimeout: const Duration(seconds: 45),
        receiveTimeout: const Duration(seconds: 60),
      ),
    );
    // Ignore SSL certificate validation errors on third-party scrapers/CDNs
    (dio.httpClientAdapter as IOHttpClientAdapter).createHttpClient = () {
      final client = HttpClient();
      client.badCertificateCallback = (X509Certificate cert, String host, int port) => true;
      return client;
    };
    return dio;
  }

  static Map<String, String> _getHeaders() {
    return {
      'x-api-key': AppConfig.apiKey,
      'x-client-version': AppConfig.clientVersion,
      'ngrok-skip-browser-warning': '1',
      'User-Agent': AppConfig.defaultUserAgent,
    };
  }

  /// Check if an input query is a direct media/social URL (Instagram, YouTube, TikTok, etc.)
  static bool isDirectUrl(String input) {
    final clean = input.trim().toLowerCase();
    return clean.startsWith('http://') ||
        clean.startsWith('https://') ||
        clean.contains('instagram.com') ||
        clean.contains('youtube.com') ||
        clean.contains('youtu.be') ||
        clean.contains('tiktok.com') ||
        clean.contains('twitter.com') ||
        clean.contains('x.com');
  }

  /// Search for shows and movies
  static Future<List<ShowModel>> search(String query, {String? siteFilter}) async {
    final cleanQuery = query.trim();
    if (cleanQuery.isEmpty) return [];

    final url = "${AppConfig.serverUrl}/api/v1/search";
    final queryParams = <String, dynamic>{
      'q': cleanQuery,
      'key': AppConfig.apiKey,
    };
    if (siteFilter != null && siteFilter.isNotEmpty && siteFilter != 'all') {
      queryParams['sites'] = siteFilter;
    }

    try {
      final response = await _dio.get(
        url,
        queryParameters: queryParams,
        options: Options(headers: _getHeaders()),
      );

      if (response.statusCode == 200 && response.data != null) {
        final data = response.data is String ? jsonDecode(response.data) : response.data;
        final results = data['results'] as List<dynamic>? ?? [];
        return results.map((r) => ShowModel.fromJson(r as Map<String, dynamic>)).toList();
      }
      return [];
    } on DioException catch (e) {
      throw _handleDioError(e, "Search failed");
    }
  }

  /// List episodes for a show URL with guaranteed sequential fallback
  static Future<List<EpisodeModel>> listEpisodes(String showUrl) async {
    final url = "${AppConfig.serverUrl}/api/v1/episodes";
    try {
      final response = await _dio.get(
        url,
        queryParameters: {
          'url': showUrl,
          'key': AppConfig.apiKey,
        },
        options: Options(headers: _getHeaders()),
      );

      if (response.statusCode == 200 && response.data != null) {
        final data = response.data is String ? jsonDecode(response.data) : response.data;
        final eps = data['episodes'] as List<dynamic>? ?? [];
        final List<EpisodeModel> parsedList = [];
        for (int i = 0; i < eps.length; i++) {
          final item = eps[i] as Map<String, dynamic>;
          parsedList.add(EpisodeModel.fromJson(item, i + 1));
        }
        return parsedList;
      }
      return [];
    } on DioException catch (e) {
      throw _handleDioError(e, "Episode listing failed");
    }
  }

  /// Resolve a direct video / yt-dlp / social media download recipe
  static Future<Map<String, dynamic>> resolveEpisode(String episodeUrl, {String kind = 'resolve'}) async {
    final url = "${AppConfig.serverUrl}/api/v1/resolve";
    try {
      final response = await _dio.get(
        url,
        queryParameters: {
          'url': episodeUrl,
          'kind': kind,
          'key': AppConfig.apiKey,
        },
        options: Options(headers: _getHeaders()),
      );

      if (response.statusCode == 200 && response.data != null) {
        final data = response.data is String ? jsonDecode(response.data) : response.data;
        return Map<String, dynamic>.from(data);
      }
      throw Exception("Could not resolve video link");
    } on DioException catch (e) {
      throw _handleDioError(e, "Link resolution failed");
    }
  }

  static Exception _handleDioError(DioException e, String defaultMsg) {
    if (e.response != null) {
      final status = e.response!.statusCode;
      if (status == 429) {
        return Exception("Rate limited — please wait a few seconds.");
      } else if (status == 401 || status == 403) {
        return Exception("Invalid API Key. Check server configuration in Settings.");
      } else if (status == 426) {
        return Exception("Server requires client update.");
      } else if (status == 502) {
        return Exception("Upstream scraper error — source site might be temporarily down.");
      }
    }
    return Exception("$defaultMsg: ${e.message}");
  }
}
