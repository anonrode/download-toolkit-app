class EpisodeModel {
  final int episode;
  final String title;
  final String rawLabel;
  final String quality;
  final String url;
  bool isSelected;

  EpisodeModel({
    required this.episode,
    required this.title,
    required this.rawLabel,
    required this.quality,
    required this.url,
    this.isSelected = false,
  });

  factory EpisodeModel.fromJson(Map<String, dynamic> json, [int fallbackIndex = 1]) {
    final rawLabel = (json['label'] ?? json['title'] ?? json['name'] ?? '').toString().trim();
    final url = (json['url'] ?? '').toString().trim();

    // 1. Extract quality tag (e.g. 480p, 720p, 1080p, HD)
    String quality = "HD";
    final qualityMatch = RegExp(r'\b(480p|720p|1080p|2160p|4k|hd)\b', caseSensitive: false).firstMatch(rawLabel);
    if (qualityMatch != null) {
      quality = qualityMatch.group(1)!.toUpperCase();
    }

    // 2. Extract Episode Number using multi-pattern regex
    int epNum = 0;
    if (json['episode'] is int && json['episode'] > 0) {
      epNum = json['episode'];
    } else {
      // Try regex on rawLabel and url
      final patterns = [
        RegExp(r'[sS]\d+[eE](\d+)', caseSensitive: false), // S01E02
        RegExp(r'\b[eE](\d+)\b', caseSensitive: false),     // E02, E2
        RegExp(r'episode[\s._-]*(\d+)', caseSensitive: false), // Episode 02
        RegExp(r'ep[\s._-]*(\d+)', caseSensitive: false),      // Ep 02
        RegExp(r'[-_](\d{1,3})[-_.]'),                       // _02. or -02-
        RegExp(r'\b(\d{1,3})\b'),                            // standalone number
      ];

      for (final p in patterns) {
        final match = p.firstMatch(rawLabel);
        if (match != null && match.groupCount >= 1) {
          final parsed = int.tryParse(match.group(1)!);
          if (parsed != null && parsed > 0 && parsed < 1000) {
            epNum = parsed;
            break;
          }
        }
      }

      // Fallback: Check URL slug
      if (epNum == 0 && url.isNotEmpty) {
        for (final p in patterns) {
          final match = p.firstMatch(url);
          if (match != null && match.groupCount >= 1) {
            final parsed = int.tryParse(match.group(1)!);
            if (parsed != null && parsed > 0 && parsed < 1000) {
              epNum = parsed;
              break;
            }
          }
        }
      }
    }

    // Guarantee non-zero episode number
    if (epNum <= 0) {
      epNum = fallbackIndex;
    }

    // 3. Format clean title
    final cleanTitle = "Episode ${epNum.toString().padLeft(2, '0')}";

    return EpisodeModel(
      episode: epNum,
      title: cleanTitle,
      rawLabel: rawLabel.isNotEmpty ? rawLabel : cleanTitle,
      quality: quality,
      url: url,
    );
  }
}
