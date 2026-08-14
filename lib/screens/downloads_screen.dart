import 'package:flutter/material.dart';
import '../models/download_task.dart';
import '../services/download_manager.dart';

class DownloadsScreen extends StatefulWidget {
  const DownloadsScreen({super.key});

  @override
  State<DownloadsScreen> createState() => _DownloadsScreenState();
}

class _DownloadsScreenState extends State<DownloadsScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  String _formatBytes(int bytes) {
    if (bytes <= 0) return "0 MB";
    final mb = bytes / (1024 * 1024);
    if (mb > 1024) {
      return "${(mb / 1024).toStringAsFixed(1)} GB";
    }
    return "${mb.toStringAsFixed(1)} MB";
  }

  String _calculateEta(DownloadTask task) {
    if (task.speedBytesPerSec <= 0 || task.totalBytes <= 0) return "--";
    final remainingBytes = task.totalBytes - task.downloadedBytes;
    if (remainingBytes <= 0) return "Done";
    final seconds = (remainingBytes / task.speedBytesPerSec).round();
    if (seconds < 60) return "${seconds}s left";
    final minutes = (seconds / 60).floor();
    final remainingSecs = seconds % 60;
    return "${minutes}m ${remainingSecs}s left";
  }

  @override
  Widget build(BuildContext context) {
    final downloadManager = DownloadManager.instance;

    return Scaffold(
      backgroundColor: const Color(0xFF08090C),
      appBar: AppBar(
        backgroundColor: const Color(0xFF08090C),
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new_rounded, color: Colors.white70, size: 20),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: const Text(
          "Downloads",
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: Colors.white),
        ),
        centerTitle: true,
      ),
      body: SafeArea(
        child: AnimatedBuilder(
          animation: downloadManager,
          builder: (context, _) {
            final active = [...downloadManager.activeTasks, ...downloadManager.queuedTasks];
            final completed = downloadManager.completedTasks;

            return Column(
              children: [
                // 1. Compact Storage Bar
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    decoration: BoxDecoration(
                      color: const Color(0xFF141722),
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(color: Colors.white.withOpacity(0.06)),
                    ),
                    child: Column(
                      children: [
                        const Row(
                          children: [
                            Icon(Icons.sd_storage_rounded, size: 16, color: Color(0xFF00E5FF)),
                            SizedBox(width: 8),
                            Text(
                              "Internal Storage",
                              style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13),
                            ),
                            Spacer(),
                            Text(
                              "45.2 GB Free of 128 GB",
                              style: TextStyle(color: Colors.white70, fontSize: 12),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(3),
                          child: const LinearProgressIndicator(
                            value: 0.64,
                            backgroundColor: Colors.white10,
                            color: Color(0xFF00E5FF),
                            minHeight: 4,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),

                // 2. Tab Bar
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                  child: Container(
                    padding: const EdgeInsets.all(4),
                    decoration: BoxDecoration(
                      color: const Color(0xFF141722),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: TabBar(
                      controller: _tabController,
                      indicator: BoxDecoration(
                        color: const Color(0xFF00E5FF),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      indicatorSize: TabBarIndicatorSize.tab,
                      labelColor: Colors.black,
                      unselectedLabelColor: Colors.white60,
                      labelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                      dividerColor: Colors.transparent,
                      tabs: [
                        Tab(text: "Active (${active.length})"),
                        Tab(text: "Completed (${completed.length})"),
                      ],
                    ),
                  ),
                ),

                // 3. Tab Views
                Expanded(
                  child: TabBarView(
                    controller: _tabController,
                    children: [
                      // Active Downloads
                      active.isEmpty
                          ? _buildEmptyState(Icons.download_done_rounded, "No active downloads")
                          : ListView.builder(
                              padding: const EdgeInsets.all(16),
                              itemCount: active.length,
                              itemBuilder: (context, index) {
                                final task = active[index];
                                return _buildActiveCard(task, downloadManager);
                              },
                            ),

                      // Completed Downloads
                      completed.isEmpty
                          ? _buildEmptyState(Icons.video_library_outlined, "No completed downloads yet")
                          : ListView.builder(
                              padding: const EdgeInsets.all(16),
                              itemCount: completed.length,
                              itemBuilder: (context, index) {
                                final task = completed[index];
                                return _buildCompletedCard(task, downloadManager);
                              },
                            ),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildActiveCard(DownloadTask task, DownloadManager dm) {
    final speedMb = (task.speedBytesPerSec / (1024 * 1024)).toStringAsFixed(1);
    final isResolving = task.status == DownloadStatus.resolving;
    final isDownloading = task.status == DownloadStatus.downloading;
    final isPaused = task.status == DownloadStatus.paused;
    final isQueued = task.status == DownloadStatus.queued;
    final eta = _calculateEta(task);

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFF141722),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white.withOpacity(0.06)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  "${task.showName}: ${task.episodeTitle}",
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: 8),
              if (isDownloading)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: const Color(0xFF00E5FF).withOpacity(0.15),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    "● $speedMb MB/s • $eta",
                    style: const TextStyle(color: Color(0xFF00E5FF), fontSize: 11, fontWeight: FontWeight.bold),
                  ),
                )
              else
                Text(
                  isResolving ? "Resolving..." : (isQueued ? "Queued" : (isPaused ? "Paused" : "Failed")),
                  style: TextStyle(
                    color: isPaused ? Colors.amberAccent : Colors.white54,
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                  ),
                ),
            ],
          ),
          const SizedBox(height: 10),

          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: task.progressPercent > 0 ? task.progressPercent : null,
              backgroundColor: Colors.white10,
              color: isPaused ? Colors.amberAccent : const Color(0xFF00E5FF),
              minHeight: 5,
            ),
          ),
          const SizedBox(height: 8),

          Row(
            children: [
              Text(
                "${_formatBytes(task.downloadedBytes)} / ${_formatBytes(task.totalBytes)} (${(task.progressPercent * 100).toStringAsFixed(0)}%)",
                style: const TextStyle(color: Colors.white54, fontSize: 11),
              ),
              const Spacer(),
              // Pause / Resume
              InkWell(
                onTap: () {
                  if (isDownloading || isResolving) {
                    dm.pause(task.id);
                  } else {
                    dm.resume(task.id);
                  }
                },
                borderRadius: BorderRadius.circular(12),
                child: Container(
                  padding: const EdgeInsets.all(6),
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.06),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(
                    isPaused ? Icons.play_arrow_rounded : Icons.pause_rounded,
                    color: Colors.white70,
                    size: 18,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              // Cancel
              InkWell(
                onTap: () => dm.cancel(task.id),
                borderRadius: BorderRadius.circular(12),
                child: Container(
                  padding: const EdgeInsets.all(6),
                  decoration: BoxDecoration(
                    color: Colors.redAccent.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(Icons.close_rounded, color: Colors.redAccent, size: 18),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildCompletedCard(DownloadTask task, DownloadManager dm) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: const Color(0xFF141722),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white.withOpacity(0.04)),
      ),
      child: Row(
        children: [
          // Green Checkmark Squircle
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: const Color(0xFF10B981).withOpacity(0.12),
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(Icons.check_rounded, color: Color(0xFF10B981), size: 20),
          ),
          const SizedBox(width: 12),

          // Title & Size
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  "${task.showName}: ${task.episodeTitle}",
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 2),
                Text(
                  _formatBytes(task.totalBytes > 0 ? task.totalBytes : task.downloadedBytes),
                  style: const TextStyle(color: Colors.white38, fontSize: 11),
                ),
              ],
            ),
          ),

          // Play Button
          InkWell(
            onTap: () => dm.openFile(task),
            borderRadius: BorderRadius.circular(12),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: const Color(0xFF00E5FF).withOpacity(0.12),
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Row(
                children: [
                  Icon(Icons.play_arrow_rounded, color: Color(0xFF00E5FF), size: 16),
                  SizedBox(width: 4),
                  Text("Play", style: TextStyle(color: Color(0xFF00E5FF), fontSize: 12, fontWeight: FontWeight.bold)),
                ],
              ),
            ),
          ),
          const SizedBox(width: 8),

          // Delete Button
          InkWell(
            onTap: () async {
              final confirm = await showDialog<bool>(
                context: context,
                builder: (context) => AlertDialog(
                  backgroundColor: const Color(0xFF141722),
                  title: const Text("Delete Download?", style: TextStyle(color: Colors.white)),
                  content: Text("Delete '${task.episodeTitle}' from device storage?", style: const TextStyle(color: Colors.white70)),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.of(context).pop(false),
                      child: const Text("Cancel", style: TextStyle(color: Colors.white54)),
                    ),
                    TextButton(
                      onPressed: () => Navigator.of(context).pop(true),
                      child: const Text("Delete", style: TextStyle(color: Colors.redAccent, fontWeight: FontWeight.bold)),
                    ),
                  ],
                ),
              );
              if (confirm == true) {
                dm.deleteDownload(task);
              }
            },
            borderRadius: BorderRadius.circular(12),
            child: Container(
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: Colors.redAccent.withOpacity(0.08),
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Icon(Icons.delete_outline_rounded, color: Colors.redAccent, size: 18),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState(IconData icon, String message) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 48, color: Colors.white.withOpacity(0.15)),
          const SizedBox(height: 12),
          Text(message, style: const TextStyle(color: Colors.white54, fontSize: 13)),
        ],
      ),
    );
  }
}
