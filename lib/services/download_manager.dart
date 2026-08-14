import 'dart:async';
import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:open_filex/open_filex.dart';
import 'package:permission_handler/permission_handler.dart';
import '../config/app_config.dart';
import '../models/download_task.dart';
import 'api_service.dart';

class DownloadManager extends ChangeNotifier {
  static final DownloadManager instance = DownloadManager._internal();
  DownloadManager._internal();

  final List<DownloadTask> _tasks = [];
  final Map<String, CancelToken> _cancelTokens = {};
  final Dio _dio = Dio();
  bool _isProcessingQueue = false;

  List<DownloadTask> get tasks => List.unmodifiable(_tasks);

  List<DownloadTask> get activeTasks =>
      _tasks.where((t) => t.status == DownloadStatus.downloading || t.status == DownloadStatus.resolving).toList();

  List<DownloadTask> get queuedTasks =>
      _tasks.where((t) => t.status == DownloadStatus.queued).toList();

  List<DownloadTask> get completedTasks =>
      _tasks.where((t) => t.status == DownloadStatus.completed).toList();

  /// Enqueue a new episode for download
  Future<void> enqueue({
    required String showName,
    required int episodeNumber,
    required String episodeTitle,
    required String originalUrl,
  }) async {
    // Request storage permissions on Android
    await _requestPermissions();

    final showDir = AppConfig.getAnonDownloadDirectory(showName);
    if (!await showDir.exists()) {
      await showDir.create(recursive: true);
    }

    final sanitizedShow = showName.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_').trim();
    final sanitizedEp = episodeTitle.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_').trim();
    final fileName = "${sanitizedShow}_E${episodeNumber.toString().padLeft(2, '0')}_$sanitizedEp.mp4";

    final targetPath = "${showDir.path}/$fileName";
    final tempPath = "$targetPath.part";

    final taskId = "${originalUrl}_${DateTime.now().millisecondsSinceEpoch}";
    final task = DownloadTask(
      id: taskId,
      showName: showName,
      episodeNumber: episodeNumber,
      episodeTitle: episodeTitle,
      originalUrl: originalUrl,
      targetFilePath: targetPath,
      tempFilePath: tempPath,
      status: DownloadStatus.queued,
    );

    _tasks.insert(0, task);
    notifyListeners();

    _processQueue();
  }

  /// Pause an ongoing download
  void pause(String taskId) {
    final task = _tasks.firstWhere((t) => t.id == taskId);
    if (task.status == DownloadStatus.downloading || task.status == DownloadStatus.resolving) {
      if (_cancelTokens.containsKey(taskId)) {
        _cancelTokens[taskId]?.cancel("User paused download");
        _cancelTokens.remove(taskId);
      }
      task.status = DownloadStatus.paused;
      task.speedBytesPerSec = 0;
      notifyListeners();
      _processQueue();
    }
  }

  /// Resume a paused download
  void resume(String taskId) {
    final task = _tasks.firstWhere((t) => t.id == taskId);
    if (task.status == DownloadStatus.paused || task.status == DownloadStatus.failed) {
      task.status = DownloadStatus.queued;
      task.errorMessage = null;
      notifyListeners();
      _processQueue();
    }
  }

  /// Cancel and remove a task
  Future<void> cancel(String taskId) async {
    final taskIndex = _tasks.indexWhere((t) => t.id == taskId);
    if (taskIndex != -1) {
      final task = _tasks[taskIndex];
      if (_cancelTokens.containsKey(taskId)) {
        _cancelTokens[taskId]?.cancel("Cancelled by user");
        _cancelTokens.remove(taskId);
      }
      // Clean up part file if incomplete
      final tempFile = File(task.tempFilePath);
      if (await tempFile.exists()) {
        try {
          await tempFile.delete();
        } catch (_) {}
      }
      _tasks.removeAt(taskIndex);
      notifyListeners();
      _processQueue();
    }
  }

  /// Open downloaded file in external player (VLC, MX Player, etc.)
  Future<void> openFile(DownloadTask task) async {
    final file = File(task.targetFilePath);
    if (await file.exists()) {
      await OpenFilex.open(task.targetFilePath);
    }
  }

  /// Internal Queue Processor
  Future<void> _processQueue() async {
    if (_isProcessingQueue) return;
    _isProcessingQueue = true;

    try {
      final runningCount = _tasks.where((t) => t.status == DownloadStatus.downloading || t.status == DownloadStatus.resolving).length;
      final slotsAvailable = AppConfig.maxConcurrentDownloads - runningCount;

      if (slotsAvailable > 0) {
        final toStart = _tasks.where((t) => t.status == DownloadStatus.queued).take(slotsAvailable).toList();
        for (final task in toStart) {
          _startTask(task);
        }
      }
    } finally {
      _isProcessingQueue = false;
    }
  }

  /// Start or resume a single download task
  Future<void> _startTask(DownloadTask task) async {
    task.status = DownloadStatus.resolving;
    notifyListeners();

    // 1. Resolve direct CDN link & hotlink headers if needed
    try {
      if (task.resolvedCdnUrl == null || task.resolvedCdnUrl!.isEmpty) {
        final recipe = await ApiService.resolveEpisode(task.originalUrl);
        task.resolvedCdnUrl = recipe['url'] as String;
        final rawHeaders = recipe['headers'] as Map<String, dynamic>? ?? {};
        task.headers = rawHeaders.map((k, v) => MapEntry(k, v.toString()));
      }
    } catch (e) {
      task.status = DownloadStatus.failed;
      task.errorMessage = "Failed to resolve link: $e";
      notifyListeners();
      _processQueue();
      return;
    }

    // 2. Check existing .part file for Range resume offset
    final tempFile = File(task.tempFilePath);
    int existingBytes = 0;
    if (await tempFile.exists()) {
      existingBytes = await tempFile.length();
    }
    task.downloadedBytes = existingBytes;
    task.status = DownloadStatus.downloading;
    notifyListeners();

    final cancelToken = CancelToken();
    _cancelTokens[task.id] = cancelToken;

    IOSink? sink;
    int lastDownloaded = existingBytes;
    DateTime lastTime = DateTime.now();

    try {
      final reqHeaders = Map<String, dynamic>.from(task.headers);
      if (existingBytes > 0) {
        reqHeaders['Range'] = 'bytes=$existingBytes-';
      }
      if (!reqHeaders.containsKey('User-Agent')) {
        reqHeaders['User-Agent'] = AppConfig.defaultUserAgent;
      }

      final response = await _dio.get<ResponseBody>(
        task.resolvedCdnUrl!,
        options: Options(
          responseType: ResponseType.stream,
          headers: reqHeaders,
          followRedirects: true,
          validateStatus: (status) => status != null && status < 400,
        ),
        cancelToken: cancelToken,
      );

      final isPartial = response.statusCode == 206;
      final contentLengthHeader = response.headers.value(HttpHeaders.contentLengthHeader);
      int incomingLength = contentLengthHeader != null ? int.tryParse(contentLengthHeader) ?? 0 : 0;

      if (isPartial) {
        task.totalBytes = existingBytes + incomingLength;
        sink = tempFile.openWrite(mode: FileMode.append);
      } else {
        // Full file restart
        task.totalBytes = incomingLength;
        task.downloadedBytes = 0;
        existingBytes = 0;
        sink = tempFile.openWrite(mode: FileMode.write);
      }

      final stream = response.data!.stream;
      await for (final chunk in stream) {
        if (task.status != DownloadStatus.downloading) break;

        sink.add(chunk);
        task.downloadedBytes += chunk.length;

        // Calculate smooth download speed every 500ms
        final now = DateTime.now();
        final diffMs = now.difference(lastTime).inMilliseconds;
        if (diffMs >= 500) {
          final bytesDiff = task.downloadedBytes - lastDownloaded;
          task.speedBytesPerSec = (bytesDiff / (diffMs / 1000.0));
          lastDownloaded = task.downloadedBytes;
          lastTime = now;
          notifyListeners();
        }
      }

      await sink.flush();
      await sink.close();
      sink = null;

      // 3. Complete and atomically rename from .part to final target file
      if (task.status == DownloadStatus.downloading) {
        final targetFile = File(task.targetFilePath);
        if (await targetFile.exists()) {
          await targetFile.delete();
        }
        await tempFile.rename(task.targetFilePath);

        task.status = DownloadStatus.completed;
        task.speedBytesPerSec = 0;
        notifyListeners();
      }
    } catch (e) {
      if (sink != null) {
        try {
          await sink.flush();
          await sink.close();
        } catch (_) {}
      }

      if (task.status != DownloadStatus.paused) {
        task.status = DownloadStatus.failed;
        task.errorMessage = e is DioException && e.type == DioExceptionType.cancel
            ? "Paused"
            : "Download error: $e";
        task.speedBytesPerSec = 0;
        notifyListeners();
      }
    } finally {
      _cancelTokens.remove(task.id);
      _processQueue();
    }
  }

  Future<void> _requestPermissions() async {
    if (Platform.isAndroid) {
      await Permission.storage.request();
      await Permission.notification.request();
      if (await Permission.manageExternalStorage.isRestricted == false) {
        await Permission.manageExternalStorage.request();
      }
    }
  }
}
