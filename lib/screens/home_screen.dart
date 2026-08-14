import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../config/app_config.dart';
import '../models/show_model.dart';
import '../models/download_task.dart';
import '../services/api_service.dart';
import '../services/download_manager.dart';
import 'episode_sheet.dart';
import 'server_sheet.dart';
import 'downloads_screen.dart';
import 'social_download_modal.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final TextEditingController _searchController = TextEditingController();
  List<ShowModel> _results = [];
  bool _isLoading = false;
  String? _error;
  String _selectedFilter = 'all';

  final List<Map<String, String>> _filterOptions = [
    {'label': 'All Sites', 'value': 'all'},
    {'label': 'Nkiri', 'value': 'nkiri'},
    {'label': 'DramaKey', 'value': 'dramakey'},
    {'label': 'PlutoMovies', 'value': 'plutomovies'},
  ];

  @override
  void initState() {
    super.initState();
    _checkClipboard();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _checkClipboard() async {
    try {
      final data = await Clipboard.getData(Clipboard.kTextPlain);
      final text = data?.text?.trim() ?? '';
      if (text.isNotEmpty && ApiService.isDirectUrl(text)) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text("Copied media link detected"),
            action: SnackBarAction(
              label: "Download",
              textColor: const Color(0xFF00E5FF),
              onPressed: () => _handleDirectUrl(text),
            ),
            backgroundColor: const Color(0xFF141722),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    } catch (_) {}
  }

  void _handleDirectUrl(String url) {
    if (AppConfig.instantSocialDownload) {
      DownloadManager.instance.enqueueDirectMedia(
        title: "Social Video",
        originalUrl: url,
      );
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text("Downloading video to Anon folder..."),
          backgroundColor: Color(0xFF141722),
          behavior: SnackBarBehavior.floating,
        ),
      );
    } else {
      showModalBottomSheet(
        context: context,
        isScrollControlled: true,
        backgroundColor: Colors.transparent,
        builder: (context) => SocialDownloadModal(url: url),
      );
    }
  }

  Future<void> _performSearch() async {
    final query = _searchController.text.trim();
    if (query.isEmpty) return;

    if (ApiService.isDirectUrl(query)) {
      _handleDirectUrl(query);
      return;
    }

    FocusScope.of(context).unfocus();
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final shows = await ApiService.search(
        query,
        siteFilter: _selectedFilter == 'all' ? null : _selectedFilter,
      );
      setState(() {
        _results = shows;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString().replaceAll("Exception: ", "");
        _isLoading = false;
      });
    }
  }

  void _openEpisodeSheet(ShowModel show) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => EpisodeSheet(show: show),
    );
  }

  void _openServerSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: const Color(0xFF141722),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (context) => const ServerSheet(),
    );
  }

  void _openDownloadsScreen() {
    Navigator.of(context).push(
      MaterialUiRoute(builder: (context) => const DownloadsScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final downloadManager = DownloadManager.instance;

    return Scaffold(
      backgroundColor: const Color(0xFF08090C),
      body: SafeArea(
        child: Column(
          children: [
            // 1. Floating Top Header (Namida style)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                decoration: BoxDecoration(
                  color: const Color(0xFF141722),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: Colors.white.withOpacity(0.06)),
                ),
                child: Row(
                  children: [
                    const Text(
                      "ANONRODE",
                      style: TextStyle(
                        fontWeight: FontWeight.w900,
                        fontSize: 18,
                        letterSpacing: 1.5,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.08),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Row(
                        children: [
                          Icon(Icons.sd_storage_rounded, size: 12, color: Colors.white70),
                          SizedBox(width: 4),
                          Text("45.2 GB Free", style: TextStyle(fontSize: 11, color: Colors.white70, fontWeight: FontWeight.w600)),
                        ],
                      ),
                    ),
                    const Spacer(),
                    // Settings Icon
                    InkWell(
                      onTap: _openServerSheet,
                      borderRadius: BorderRadius.circular(12),
                      child: Container(
                        padding: const EdgeInsets.all(6),
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.06),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: const Icon(Icons.tune_rounded, color: Colors.white70, size: 18),
                      ),
                    ),
                    const SizedBox(width: 8),
                    // Downloads Queue Button
                    AnimatedBuilder(
                      animation: downloadManager,
                      builder: (context, _) {
                        final activeCount = downloadManager.activeTasks.length + downloadManager.queuedTasks.length;
                        return InkWell(
                          onTap: _openDownloadsScreen,
                          borderRadius: BorderRadius.circular(12),
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                            decoration: BoxDecoration(
                              color: activeCount > 0 ? const Color(0xFF00E5FF).withOpacity(0.15) : Colors.white.withOpacity(0.06),
                              borderRadius: BorderRadius.circular(12),
                              border: activeCount > 0 ? Border.all(color: const Color(0xFF00E5FF).withOpacity(0.4)) : null,
                            ),
                            child: Row(
                              children: [
                                Icon(
                                  Icons.download_rounded,
                                  color: activeCount > 0 ? const Color(0xFF00E5FF) : Colors.white70,
                                  size: 18,
                                ),
                                if (activeCount > 0) ...[
                                  const SizedBox(width: 4),
                                  Text(
                                    "$activeCount",
                                    style: const TextStyle(
                                      color: Color(0xFF00E5FF),
                                      fontWeight: FontWeight.bold,
                                      fontSize: 12,
                                    ),
                                  ),
                                ],
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                  ],
                ),
              ),
            ),

            // 2. Floating Search Capsule (Seal style)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
              child: Container(
                padding: const EdgeInsets.fromLTRB(16, 2, 6, 2),
                decoration: BoxDecoration(
                  color: const Color(0xFF141722),
                  borderRadius: BorderRadius.circular(28),
                  border: Border.all(color: Colors.white.withOpacity(0.08)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.search_rounded, color: Colors.white54, size: 20),
                    const SizedBox(width: 10),
                    Expanded(
                      child: TextField(
                        controller: _searchController,
                        style: const TextStyle(color: Colors.white, fontSize: 14),
                        decoration: const InputDecoration(
                          hintText: "Search drama or paste link...",
                          hintStyle: TextStyle(color: Colors.white38, fontSize: 13),
                          border: InputBorder.none,
                          isDense: true,
                        ),
                        onSubmitted: (_) => _performSearch(),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.content_paste_rounded, color: Colors.white54, size: 18),
                      tooltip: "Paste",
                      onPressed: () async {
                        final data = await Clipboard.getData(Clipboard.kTextPlain);
                        if (data?.text != null) {
                          _searchController.text = data!.text!;
                          _performSearch();
                        }
                      },
                    ),
                    InkWell(
                      onTap: _performSearch,
                      borderRadius: BorderRadius.circular(22),
                      child: Container(
                        padding: const EdgeInsets.all(10),
                        decoration: const BoxDecoration(
                          color: Color(0xFF00E5FF),
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(Icons.arrow_downward_rounded, color: Colors.black, size: 18),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            // 3. Filter Chips
            SizedBox(
              height: 44,
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                itemCount: _filterOptions.length,
                itemBuilder: (context, index) {
                  final opt = _filterOptions[index];
                  final isSelected = _selectedFilter == opt['value'];
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ChoiceChip(
                      label: Text(
                        opt['label']!,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                          color: isSelected ? Colors.black : Colors.white70,
                        ),
                      ),
                      selected: isSelected,
                      selectedColor: const Color(0xFF00E5FF),
                      backgroundColor: const Color(0xFF141722),
                      side: BorderSide(
                        color: isSelected ? const Color(0xFF00E5FF) : Colors.white.withOpacity(0.06),
                      ),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      onSelected: (selected) {
                        if (selected) {
                          setState(() => _selectedFilter = opt['value']!);
                          if (_searchController.text.trim().isNotEmpty) {
                            _performSearch();
                          }
                        }
                      },
                    ),
                  );
                },
              ),
            ),

            // 4. Drama Results Grid (2-Column Squircle Grid)
            Expanded(
              child: _isLoading
                  ? const Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          CircularProgressIndicator(color: Color(0xFF00E5FF)),
                          SizedBox(height: 16),
                          Text("Scanning sources...", style: TextStyle(color: Colors.white54, fontSize: 13)),
                        ],
                      ),
                    )
                  : _error != null
                      ? Center(
                          child: Padding(
                            padding: const EdgeInsets.all(24),
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                const Icon(Icons.error_outline_rounded, color: Colors.redAccent, size: 40),
                                const SizedBox(height: 12),
                                Text(_error!, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white70)),
                                const SizedBox(height: 16),
                                ElevatedButton(
                                  onPressed: _performSearch,
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: const Color(0xFF141722),
                                    foregroundColor: const Color(0xFF00E5FF),
                                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                                  ),
                                  child: const Text("Retry Search"),
                                ),
                              ],
                            ),
                          ),
                        )
                      : _results.isEmpty
                          ? Center(
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  Icon(Icons.movie_filter_rounded, size: 54, color: Colors.white.withOpacity(0.15)),
                                  const SizedBox(height: 14),
                                  const Text("Search a drama or paste a link", style: TextStyle(color: Colors.white54, fontSize: 14)),
                                  const SizedBox(height: 4),
                                  const Text("NKiri • DramaKey • Pluto • Instagram • YouTube", style: TextStyle(color: Colors.white24, fontSize: 11)),
                                ],
                              ),
                            )
                          : GridView.builder(
                              padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                                crossAxisCount: 2,
                                childAspectRatio: 0.72,
                                crossAxisSpacing: 12,
                                mainAxisSpacing: 12,
                              ),
                              itemCount: _results.length,
                              itemBuilder: (context, index) {
                                final show = _results[index];
                                return _buildDramaCard(show);
                              },
                            ),
            ),

            // 5. Floating Compact Mini-Download Bar (Seal Style)
            AnimatedBuilder(
              animation: downloadManager,
              builder: (context, _) {
                final active = downloadManager.activeTasks;
                if (active.isEmpty) return const SizedBox.shrink();
                final current = active.first;
                final pct = current.progressPercent;
                final speedMb = (current.speedBytesPerSec / (1024 * 1024)).toStringAsFixed(1);

                return Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                  child: InkWell(
                    onTap: _openDownloadsScreen,
                    borderRadius: BorderRadius.circular(20),
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: const Color(0xFF141722),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: const Color(0xFF00E5FF).withOpacity(0.3)),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withOpacity(0.4),
                            blurRadius: 12,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Expanded(
                                child: Text(
                                  "${current.showName} • ${current.episodeTitle}",
                                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: Colors.white),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              const SizedBox(width: 8),
                              Text(
                                "● $speedMb MB/s",
                                style: const TextStyle(color: Color(0xFF00E5FF), fontSize: 11, fontWeight: FontWeight.bold),
                              ),
                              const SizedBox(width: 6),
                              IconButton(
                                constraints: const BoxConstraints(),
                                padding: EdgeInsets.zero,
                                icon: Icon(
                                  current.status == DownloadStatus.downloading ? Icons.pause_circle_filled_rounded : Icons.play_circle_filled_rounded,
                                  color: Colors.white70,
                                  size: 22,
                                ),
                                onPressed: () {
                                  if (current.status == DownloadStatus.downloading) {
                                    downloadManager.pause(current.id);
                                  } else {
                                    downloadManager.resume(current.id);
                                  }
                                },
                              ),
                            ],
                          ),
                          const SizedBox(height: 6),
                          ClipRRect(
                            borderRadius: BorderRadius.circular(4),
                            child: LinearProgressIndicator(
                              value: pct > 0 ? pct : null,
                              backgroundColor: Colors.white10,
                              color: const Color(0xFF00E5FF),
                              minHeight: 4,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDramaCard(ShowModel show) {
    return InkWell(
      onTap: () => _openEpisodeSheet(show),
      borderRadius: BorderRadius.circular(20),
      child: Container(
        decoration: BoxDecoration(
          color: const Color(0xFF141722),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: Colors.white.withOpacity(0.06)),
        ),
        clipBehavior: Clip.antiAlias,
        child: Stack(
          children: [
            // Poster Image / Gradient Fallback
            Positioned.fill(
              child: show.poster != null
                  ? Image.network(
                      show.poster!,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => _buildFallbackPoster(show),
                    )
                  : _buildFallbackPoster(show),
            ),
            // Gradient Overlay
            Positioned.fill(
              child: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Colors.transparent,
                      Colors.black.withOpacity(0.2),
                      Colors.black.withOpacity(0.9),
                    ],
                    stops: const [0.3, 0.6, 1.0],
                  ),
                ),
              ),
            ),
            // Source Badge Top Right
            Positioned(
              top: 8,
              right: 8,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.black.withOpacity(0.7),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: show.siteColor.withOpacity(0.6)),
                ),
                child: Text(
                  show.site.toUpperCase(),
                  style: TextStyle(
                    color: show.siteColor,
                    fontSize: 9,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 0.5,
                  ),
                ),
              ),
            ),
            // Title Bottom
            Positioned(
              left: 10,
              right: 10,
              bottom: 10,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    show.title,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      height: 1.2,
                    ),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      Icon(Icons.video_library_rounded, size: 11, color: Colors.white.withOpacity(0.6)),
                      const SizedBox(width: 4),
                      Text(
                        show.episodeCount > 0 ? "${show.episodeCount} Episodes" : "Episodes Available",
                        style: TextStyle(color: Colors.white.withOpacity(0.6), fontSize: 10),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFallbackPoster(ShowModel show) {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            show.siteColor.withOpacity(0.2),
            const Color(0xFF141722),
          ],
        ),
      ),
      child: Center(
        child: Icon(Icons.movie_outlined, size: 40, color: Colors.white.withOpacity(0.15)),
      ),
    );
  }
}

class MaterialUiRoute<T> extends MaterialPageRoute<T> {
  MaterialUiRoute({required super.builder});
}
