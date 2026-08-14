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
  bool _isSelectionMode = false;

  @override
  void initState() {
    super.initState();
    _fetchEpisodes();
  }

  Future<void> _fetchEpisodes() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

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

  void _downloadSingle(EpisodeModel ep) {
    DownloadManager.instance.enqueue(
      showName: widget.show.title,
      episodeNumber: ep.episode,
      episodeTitle: ep.title,
      originalUrl: ep.url,
    );

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text("Queued ${ep.title} for download in Anon folder"),
        backgroundColor: const Color(0xFF1E293B),
        behavior: SnackBarBehavior.floating,
        duration: const Duration(seconds: 2),
      ),
    );
  }

  void _downloadBatch(List<EpisodeModel> selected) {
    for (final ep in selected) {
      DownloadManager.instance.enqueue(
        showName: widget.show.title,
        episodeNumber: ep.episode,
        episodeTitle: ep.title,
        originalUrl: ep.url,
      );
    }

    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text("Queued ${selected.length} episodes for download"),
        backgroundColor: const Color(0xFF1E293B),
        behavior: SnackBarBehavior.floating,
        duration: const Duration(seconds: 3),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final selectedCount = _episodes.where((e) => e.isSelected).length;

    return Container(
      height: MediaQuery.of(context).size.height * 0.85,
      decoration: const BoxDecoration(
        color: Color(0xFF0F1117),
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Drag handle
          Center(
            child: Container(
              margin: const EdgeInsets.only(top: 12),
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.white24,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),

          // Header
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
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
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                            decoration: BoxDecoration(
                              color: const Color(0xFF1E293B),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: Text(
                              widget.show.site.toUpperCase(),
                              style: const TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                                color: Color(0xFF38BDF8),
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          if (!_isLoading && _episodes.isNotEmpty)
                            Text(
                              "${_episodes.length} Episodes available",
                              style: const TextStyle(fontSize: 12, color: Colors.white60),
                            ),
                        ],
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close, color: Colors.white70),
                  onPressed: () => Navigator.pop(context),
                ),
              ],
            ),
          ),

          const Divider(color: Colors.white10, height: 1),

          // Batch Action Bar
          if (!_isLoading && _episodes.isNotEmpty)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              color: const Color(0xFF161922),
              child: Row(
                children: [
                  TextButton.icon(
                    icon: Icon(
                      _isSelectionMode ? Icons.check_circle : Icons.select_all,
                      size: 18,
                      color: const Color(0xFF38BDF8),
                    ),
                    label: Text(
                      _isSelectionMode ? "Deselect All" : "Select All",
                      style: const TextStyle(color: Color(0xFF38BDF8), fontSize: 13),
                    ),
                    onPressed: () {
                      setState(() {
                        if (_isSelectionMode && selectedCount == _episodes.length) {
                          for (var ep in _episodes) {
                            ep.isSelected = false;
                          }
                          _isSelectionMode = false;
                        } else {
                          for (var ep in _episodes) {
                            ep.isSelected = true;
                          }
                          _isSelectionMode = true;
                        }
                      });
                    },
                  ),
                  const Spacer(),
                  if (selectedCount > 0)
                    ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF0284C7),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                      ),
                      icon: const Icon(Icons.download, size: 16),
                      label: Text("Download ($selectedCount)"),
                      onPressed: () => _downloadBatch(_episodes.where((e) => e.isSelected).toList()),
                    ),
                ],
              ),
            ),

          // Content
          Expanded(
            child: _isLoading
                ? const Center(
                    child: CircularProgressIndicator(color: Color(0xFF38BDF8)),
                  )
                : _error != null
                    ? Center(
                        child: Padding(
                          padding: const EdgeInsets.all(24.0),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(Icons.error_outline, color: Colors.redAccent, size: 40),
                              const SizedBox(height: 12),
                              Text(_error!, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white70)),
                              const SizedBox(height: 16),
                              ElevatedButton(
                                style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF1E293B)),
                                onPressed: _fetchEpisodes,
                                child: const Text("Retry"),
                              ),
                            ],
                          ),
                        ),
                      )
                    : ListView.separated(
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        itemCount: _episodes.length,
                        separatorBuilder: (_, __) => const Divider(color: Colors.white10, height: 1),
                        itemBuilder: (context, index) {
                          final ep = _episodes[index];
                          return ListTile(
                            contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 2),
                            leading: _isSelectionMode
                                ? Checkbox(
                                    value: ep.isSelected,
                                    activeColor: const Color(0xFF0284C7),
                                    onChanged: (val) {
                                      setState(() {
                                        ep.isSelected = val ?? false;
                                      });
                                    },
                                  )
                                : Container(
                                    width: 38,
                                    height: 38,
                                    alignment: Alignment.center,
                                    decoration: BoxDecoration(
                                      color: const Color(0xFF1E293B),
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                    child: Text(
                                      "E${ep.episode}",
                                      style: const TextStyle(
                                        color: Colors.white,
                                        fontWeight: FontWeight.bold,
                                        fontSize: 13,
                                      ),
                                    ),
                                  ),
                            title: Text(
                              ep.title,
                              style: const TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.w500),
                            ),
                            trailing: IconButton(
                              icon: const Icon(Icons.download_for_offline, color: Color(0xFF38BDF8)),
                              onPressed: () => _downloadSingle(ep),
                            ),
                            onTap: () {
                              if (_isSelectionMode) {
                                setState(() {
                                  ep.isSelected = !ep.isSelected;
                                });
                              } else {
                                _downloadSingle(ep);
                              }
                            },
                            onLongPress: () {
                              setState(() {
                                _isSelectionMode = true;
                                ep.isSelected = true;
                              });
                            },
                          );
                        },
                      ),
          ),
        ],
      ),
    );
  }
}
