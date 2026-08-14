import 'dart:io';

enum DownloadStatus {
  queued,
  resolving,
  downloading,
  paused,
  completed,
  failed,
}

class DownloadTask {
  final String id;
  final String showName;
  final int episodeNumber;
  final String episodeTitle;
  final String originalUrl;
  String? resolvedCdnUrl;
  Map<String, String> headers;
  String targetFilePath;
  String tempFilePath;
  
  int totalBytes;
  int downloadedBytes;
  DownloadStatus status;
  double speedBytesPerSec;
  String? errorMessage;
  final DateTime createdAt;

  DownloadTask({
    required this.id,
    required this.showName,
    required this.episodeNumber,
    required this.episodeTitle,
    required this.originalUrl,
    this.resolvedCdnUrl,
    Map<String, String>? headers,
    required this.targetFilePath,
    required this.tempFilePath,
    this.totalBytes = 0,
    this.downloadedBytes = 0,
    this.status = DownloadStatus.queued,
    this.speedBytesPerSec = 0,
    this.errorMessage,
    DateTime? createdAt,
  })  : headers = headers ?? {},
        createdAt = createdAt ?? DateTime.now();

  double get progress {
    if (totalBytes <= 0) return 0.0;
    return (downloadedBytes / totalBytes).clamp(0.0, 1.0);
  }

  double get progressPercent => progress;

  String get fileName {
    return targetFilePath.split(Platform.pathSeparator).last;
  }

  String get formattedSpeed {
    if (speedBytesPerSec <= 0 || status != DownloadStatus.downloading) return "";
    if (speedBytesPerSec >= 1024 * 1024) {
      return "${(speedBytesPerSec / (1024 * 1024)).toStringAsFixed(1)} MB/s";
    } else {
      return "${(speedBytesPerSec / 1024).toStringAsFixed(0)} KB/s";
    }
  }

  String get formattedSize {
    if (totalBytes <= 0) return "";
    final currentMB = (downloadedBytes / (1024 * 1024)).toStringAsFixed(1);
    final totalMB = (totalBytes / (1024 * 1024)).toStringAsFixed(1);
    return "$currentMB / $totalMB MB";
  }
}
