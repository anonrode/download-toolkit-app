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

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: DownloadManager.instance,
      builder: (context, _) {
        final activeList = DownloadManager.instance.tasks
            .where((t) => t.status != DownloadStatus.completed)
            .toList();
        final completedList = DownloadManager.instance.completedTasks;

        return Scaffold(
          backgroundColor: const Color(0xFF0A0A0C),
          appBar: AppBar(
            backgroundColor: const Color(0xFF0A0A0C),
            elevation: 0,
            title: const Text(
              "Download Queue",
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: Colors.white),
            ),
            bottom: TabBar(
              controller: _tabController,
              indicatorColor: const Color(0xFF38BDF8),
              labelColor: const Color(0xFF38BDF8),
              unselectedLabelColor: Colors.white54,
              tabs: [
                Tab(text: "Active (${activeList.length})"),
                Tab(text: "Completed (${completedList.length})"),
              ],
            ),
          ),
          body: TabBarView(
            controller: _tabController,
            children: [
              // Active Tab
              activeList.isEmpty
                  ? _buildEmptyState("No active downloads", Icons.download_done)
                  : ListView.builder(
                      padding: const EdgeInsets.all(16),
                      itemCount: activeList.length,
                      itemBuilder: (context, index) {
                        return _buildActiveCard(activeList[index]);
                      },
                    ),

              // Completed Tab
              completedList.isEmpty
                  ? _buildEmptyState("No completed downloads yet", Icons.folder_open)
                  : ListView.builder(
                      padding: const EdgeInsets.all(16),
                      itemCount: completedList.length,
                      itemBuilder: (context, index) {
                        return _buildCompletedCard(completedList[index]);
                      },
                    ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildEmptyState(String message, IconData icon) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 48, color: Colors.white24),
          const SizedBox(height: 12),
          Text(message, style: const TextStyle(color: Colors.white38, fontSize: 14)),
        ],
      ),
    );
  }

  Widget _buildActiveCard(DownloadTask task) {
    final isDownloading = task.status == DownloadStatus.downloading;
    final isPaused = task.status == DownloadStatus.paused;
    final isFailed = task.status == DownloadStatus.failed;
    final isResolving = task.status == DownloadStatus.resolving;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF141721),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white.withOpacity(0.06)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      task.showName,
                      style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Colors.white),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      task.episodeTitle,
                      style: const TextStyle(fontSize: 12, color: Colors.white60),
                    ),
                  ],
                ),
              ),
              // Status Badge
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: isDownloading
                      ? const Color(0xFF0284C7).withOpacity(0.2)
                      : isPaused
                          ? Colors.orange.withOpacity(0.2)
                          : isFailed
                              ? Colors.red.withOpacity(0.2)
                              : Colors.white10,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  isResolving
                      ? "Resolving..."
                      : isDownloading
                          ? "Downloading"
                          : isPaused
                              ? "Paused"
                              : isFailed
                                  ? "Failed"
                                  : "Queued",
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: isDownloading
                        ? const Color(0xFF38BDF8)
                        : isPaused
                            ? Colors.orangeAccent
                            : isFailed
                                ? Colors.redAccent
                                : Colors.white70,
                  ),
                ),
              ),
            ],
          ),

          const SizedBox(height: 12),

          // Progress Bar
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: task.totalBytes > 0 ? task.progress : null,
              backgroundColor: Colors.white10,
              valueColor: AlwaysStoppedAnimation<Color>(
                isPaused
                    ? Colors.orangeAccent
                    : isFailed
                        ? Colors.redAccent
                        : const Color(0xFF38BDF8),
              ),
              minHeight: 6,
            ),
          ),

          const SizedBox(height: 8),

          // Metrics & Controls
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                task.formattedSize.isNotEmpty
                    ? "${task.formattedSize}  ${task.formattedSpeed.isNotEmpty ? '• ${task.formattedSpeed}' : ''}"
                    : (task.errorMessage ?? "Connecting..."),
                style: TextStyle(
                  fontSize: 11,
                  color: isFailed ? Colors.redAccent : Colors.white54,
                ),
              ),
              Row(
                children: [
                  if (isDownloading)
                    IconButton(
                      icon: const Icon(Icons.pause_circle_filled, color: Colors.white70, size: 24),
                      onPressed: () => DownloadManager.instance.pause(task.id),
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(),
                    ),
                  if (isPaused || isFailed)
                    IconButton(
                      icon: const Icon(Icons.play_circle_filled, color: Color(0xFF38BDF8), size: 24),
                      onPressed: () => DownloadManager.instance.resume(task.id),
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(),
                    ),
                  const SizedBox(width: 12),
                  IconButton(
                    icon: const Icon(Icons.close, color: Colors.white38, size: 20),
                    onPressed: () => DownloadManager.instance.cancel(task.id),
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildCompletedCard(DownloadTask task) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF141721),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white.withOpacity(0.06)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: const Color(0xFF10B981).withOpacity(0.15),
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.check, color: Color(0xFF10B981), size: 20),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  task.showName,
                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Colors.white),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 2),
                Text(
                  task.fileName,
                  style: const TextStyle(fontSize: 11, color: Colors.white54),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          ElevatedButton.icon(
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF1E293B),
              foregroundColor: const Color(0xFF38BDF8),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            ),
            icon: const Icon(Icons.play_arrow, size: 16),
            label: const Text("VLC", style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
            onPressed: () => DownloadManager.instance.openFile(task),
          ),
        ],
      ),
    );
  }
}
