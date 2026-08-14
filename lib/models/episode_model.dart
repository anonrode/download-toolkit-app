class EpisodeModel {
  final int episode;
  final String title;
  final String url;
  bool isSelected;

  EpisodeModel({
    required this.episode,
    required this.title,
    required this.url,
    this.isSelected = false,
  });

  factory EpisodeModel.fromJson(Map<String, dynamic> json) {
    int epNum = 0;
    if (json['episode'] is int) {
      epNum = json['episode'];
    } else if (json['episode'] is String) {
      epNum = int.tryParse(json['episode']) ?? 0;
    }

    return EpisodeModel(
      episode: epNum,
      title: json['title'] ?? 'Episode $epNum',
      url: json['url'] ?? '',
    );
  }
}
