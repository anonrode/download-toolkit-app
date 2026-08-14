import 'package:flutter/material.dart';
import '../models/show_model.dart';
import '../models/episode_model.dart';
import '../services/api_service.dart';
import '../services/download_manager.dart';

class EpisodeSheet extends StatefulWidget {
  final ShowModel show;

  const EpisodeSheet({super.key, required this.show});

  @override
  State<EpisodeSheet> createState() => _EpisodeSheetState();
}

class _EpisodeSheetState extends State<EpisodeSheet> {
  List<EpisodeModel> _episodes = [];
  bool _isLoading = true;
  String? _error;
  String _activeFilter = 'all';

  @override
  void initState() {
    super.initState();
    _fetchEpisodes();
  }

  Future<void> _fetchEpisodes() async {
    try {
      final eps = await ApiService.listEpisodes(widget.show.url);
      setState(() {
        _episodes = eps;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString().replaceAll("Exception: ", "");
        _isLoading = false;
      });
    }
  }

  void _applyBatchFilter(String filter) {
    setState(() {
      _activeFilter = filter;
      if (filter == 'all') {
        for (var ep in _episodes) {
          ep.isSelected = true;
        }
      } else if (filter == 'none') {
        for (var ep in _episodes) {
          ep.isSelected = false;
        }
      } else if (filter == '1-8') {
        for (var ep in _episodes) {
          ep.isSelected = (ep.episode >= 1 && ep.episode <= 8);
        }
      } else if (filter == '9-16') {
        for (var ep in _episodes) {
          ep.isSelected = (ep.episode >= 9 && ep.episode <= 16);
        }
      } else if (filter == '17-24') {
        for (var ep in _episodes) {
          ep.isSelected = (ep.episode >= 17 && ep.episode <= 24);
        }
      }
    });
  }

  void _downloadSelected() {
    final selected = _episodes.where((e) => e.isSelected).toList();
    if (selected.isEmpty) return;

    for (final ep in selected) {
      DownloadManager.instance.enqueue(
        showName: widget.show.title,
        episodeNumber: ep.episode,
        episodeTitle: ep.title,
        originalUrl: ep.url,
      );
    }

    Navigator.of(context).pop();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text("Queued ${selected.length} episode(s) for download"),
        backgroundColor: const Color(0xFF141722),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final selectedCount = _episodes.where((e) => e.isSelected).length;
    final downloadManager = DownloadManager.instance;

    return DraggableScrollableSheet(
      initialChildSize: 0.85,
      minChildSize: 0.5,
      maxChildSize: 0.95,
      builder: (context, scrollController) {
        return Container(
          decoration: const BoxDecoration(
            color: Color(0xFF0F1118),
            borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
          ),
          child: Column(
            children: [
              // Drag Handle
              const SizedBox(height: 12),
              Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.white24,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 14),

              // Show Header
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            widget.show.title,
                            style: const TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                              color: Colors.white,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 4),
                          Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: widget.show.siteColor.withOpacity(0.15),
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: Text(
                                  widget.show.site.toUpperCase(),
                                  style: TextStyle(
                                    color: widget.show.siteColor,
                                    fontSize: 10,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 8),
                              Text(
                                _isLoading ? "Loading..." : "${_episodes.length} Episodes",
                                style: const TextStyle(color: Colors.white54, fontSize: 12),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close_rounded, color: Colors.white54),
                      onPressed: () => Navigator.of(context).pop(),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),

              // Batch Filter Pills
              if (!_isLoading && _episodes.isNotEmpty)
                SizedBox(
                  height: 36,
                  child: ListView(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    children: [
                      _buildBatchChip("All (${_episodes.length})", 'all'),
                      _buildBatchChip("Deselect", 'none'),
                      if (_episodes.length > 8) _buildBatchChip("Ep 1-8", '1-8'),
                      if (_episodes.length > 8) _buildBatchChip("Ep 9-16", '9-16'),
                      if (_episodes.length > 16) _buildBatchChip("Ep 17-24", '17-24'),
                    ],
                  ),
                ),
              const SizedBox(height: 8),
              const Divider(color: Colors.white10, height: 1),

              // Episodes List
              Expanded(
                child: _isLoading
                    ? const Center(
                        child: CircularProgressIndicator(color: Color(0xFF00E5FF)),
                      )
                    : _error != null
                        ? Center(
                            child: Padding(
                              padding: const EdgeInsets.all(24),
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  const Icon(Icons.error_outline_rounded, color: Colors.redAccent, size: 36),
                                  const SizedBox(height: 8),
                                  Text(_error!, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white70)),
                                  const SizedBox(height: 12),
                                  ElevatedButton(
                                    onPressed: _fetchEpisodes,
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: const Color(0xFF141722),
                                      foregroundColor: const Color(0xFF00E5FF),
                                    ),
                                    child: const Text("Retry"),
                                  ),
                                ],
                              ),
                            ),
                          )
                        : ListView.builder(
                            controller: scrollController,
                            padding: const EdgeInsets.fromLTRB(16, 8, 16, 90),
                            itemCount: _episodes.length,
                            itemBuilder: (context, index) {
                              final ep = _episodes[index];
                              final isDownloaded = downloadManager.isEpisodeDownloaded(widget.show.title, ep.episode);

                              return Padding(
                                padding: const EdgeInsets.symmetric(vertical: 4),
                                child: InkWell(
                                  onTap: () {
                                    setState(() => ep.isSelected = !ep.isSelected);
                                  },
                                  borderRadius: BorderRadius.circular(16),
                                  child: Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                                    decoration: BoxDecoration(
                                      color: ep.isSelected
                                          ? const Color(0xFF00E5FF).withOpacity(0.08)
                                          : const Color(0xFF141722),
                                      borderRadius: BorderRadius.circular(16),
                                      border: Border.all(
                                        color: ep.isSelected
                                            ? const Color(0xFF00E5FF).withOpacity(0.6)
                                            : Colors.white.withOpacity(0.04),
                                      ),
                                    ),
                                    child: Row(
                                      children: [
                                        // Squircle Episode Badge
                                        Container(
                                          width: 38,
                                          height: 38,
                                          decoration: BoxDecoration(
                                            color: ep.isSelected
                                                ? const Color(0xFF00E5FF)
                                                : const Color(0xFF1E2333),
                                            borderRadius: BorderRadius.circular(12),
                                          ),
                                          child: Center(
                                            child: Text(
                                              ep.episode.toString().padLeft(2, '0'),
                                              style: TextStyle(
                                                color: ep.isSelected ? Colors.black : Colors.white,
                                                fontWeight: FontWeight.bold,
                                                fontSize: 13,
                                              ),
                                            ),
                                          ),
                                        ),
                                        const SizedBox(width: 14),

                                        // Title & Quality
                                        Expanded(
                                          child: Column(
                                            crossAxisAlignment: CrossAxisAlignment.start,
                                            children: [
                                              Text(
                                                ep.title,
                                                style: const TextStyle(
                                                  color: Colors.white,
                                                  fontWeight: FontWeight.w600,
                                                  fontSize: 14,
                                                ),
                                              ),
                                              const SizedBox(height: 2),
                                              Text(
                                                "${ep.quality} • Fast Mirror",
                                                style: const TextStyle(color: Colors.white38, fontSize: 11),
                                              ),
                                            ],
                                          ),
                                        ),

                                        // Status / Download Icon
                                        if (isDownloaded)
                                          Container(
                                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                            decoration: BoxDecoration(
                                              color: const Color(0xFF10B981).withOpacity(0.15),
                                              borderRadius: BorderRadius.circular(8),
                                            ),
                                            child: const Row(
                                              children: [
                                                Icon(Icons.check_rounded, color: Color(0xFF10B981), size: 14),
                                                SizedBox(width: 4),
                                                Text("Saved", style: TextStyle(color: Color(0xFF10B981), fontSize: 11, fontWeight: FontWeight.bold)),
                                              ],
                                            ),
                                          )
                                        else
                                          IconButton(
                                            icon: Icon(
                                              ep.isSelected ? Icons.check_circle_rounded : Icons.download_rounded,
                                              color: ep.isSelected ? const Color(0xFF00E5FF) : Colors.white54,
                                              size: 22,
                                            ),
                                            onPressed: () {
                                              setState(() => ep.isSelected = !ep.isSelected);
                                            },
                                          ),
                                      ],
                                    ),
                                  ),
                                ),
                              );
                            },
                          ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildBatchChip(String label, String value) {
    final isSelected = _activeFilter == value;
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: InkWell(
        onTap: () => _applyBatchFilter(value),
        borderRadius: BorderRadius.circular(12),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: isSelected ? const Color(0xFF00E5FF) : const Color(0xFF141722),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: isSelected ? const Color(0xFF00E5FF) : Colors.white.withOpacity(0.06),
            ),
          ),
          child: Center(
            child: Text(
              label,
              style: TextStyle(
                color: isSelected ? Colors.black : Colors.white70,
                fontSize: 12,
                fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
