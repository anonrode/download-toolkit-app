class ShowModel {
  final String site;
  final String url;
  final String title;

  ShowModel({
    required this.site,
    required this.url,
    required this.title,
  });

  factory ShowModel.fromJson(Map<String, dynamic> json) {
    final rawUrl = json['url'] as String? ?? '';
    final site = json['site'] as String? ?? 'site';
    
    // Clean title from URL slug if title not directly passed
    String parsedTitle = json['title'] ?? '';
    if (parsedTitle.isEmpty && rawUrl.isNotEmpty) {
      final uri = Uri.tryParse(rawUrl);
      if (uri != null) {
        final segments = uri.pathSegments.where((s) => s.isNotEmpty).toList();
        if (segments.isNotEmpty) {
          parsedTitle = segments.last
              .replaceAll(RegExp(r'[-_]'), ' ')
              .replaceAll(RegExp(r'\b\w'), (m) => m.group(0)!.toUpperCase())
              .trim();
        }
      }
    }
    if (parsedTitle.isEmpty) parsedTitle = "Untitled Show";

    return ShowModel(
      site: site,
      url: rawUrl,
      title: parsedTitle,
    );
  }
}
