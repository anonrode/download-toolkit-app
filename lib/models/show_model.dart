import 'package:flutter/material.dart';

class ShowModel {
  final String site;
  final String url;
  final String title;
  final String? poster;
  final int episodeCount;

  ShowModel({
    required this.site,
    required this.url,
    required this.title,
    this.poster,
    this.episodeCount = 0,
  });

  Color get siteColor {
    switch (site.toLowerCase()) {
      case 'nkiri':
        return const Color(0xFF10B981); // Emerald
      case 'dramakey':
        return const Color(0xFF38BDF8); // Cyan
      case 'plutomovies':
        return const Color(0xFFF59E0B); // Amber
      default:
        return const Color(0xFFA855F7); // Purple
    }
  }

  factory ShowModel.fromJson(Map<String, dynamic> json) {
    final rawUrl = (json['url'] ?? '').toString().trim();
    final site = (json['site'] ?? 'Source').toString().trim();
    final poster = (json['poster'] ?? json['image'] ?? json['thumbnail'] ?? '').toString().trim();
    
    // Clean title
    String parsedTitle = (json['title'] ?? '').toString().trim();
    if (parsedTitle.isEmpty && rawUrl.isNotEmpty) {
      final uri = Uri.tryParse(rawUrl);
      if (uri != null) {
        final segments = uri.pathSegments.where((s) => s.isNotEmpty).toList();
        if (segments.isNotEmpty) {
          parsedTitle = segments.last
              .replaceAll(RegExp(r'[-_]'), ' ')
              .replaceAll(RegExp(r'\b(korean|drama|season|\d+p|movie|download|complete|hd)\b', caseSensitive: false), '')
              .replaceAllMapped(RegExp(r'\b\w'), (m) => m.group(0)!.toUpperCase())
              .trim();
        }
      }
    }
    if (parsedTitle.isEmpty) parsedTitle = "Untitled Drama";

    final epCount = json['episodes_count'] is int ? json['episodes_count'] as int : 0;

    return ShowModel(
      site: site,
      url: rawUrl,
      title: parsedTitle,
      poster: poster.isNotEmpty ? poster : null,
      episodeCount: epCount,
    );
  }
}
