import 'package:flutter/material.dart';
import '../models/show_model.dart';
import '../services/api_service.dart';
import '../services/download_manager.dart';
import 'episode_sheet.dart';
import 'server_sheet.dart';
import 'downloads_screen.dart';

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
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _performSearch() async {
    final query = _searchController.text.trim();
    if (query.isEmpty) return;

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
      backgroundColor: const Color(0xFF0F1117),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (context) => const ServerSheet(),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0A0A0C),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0A0A0C),
        elevation: 0,
        title: const Text(
          "ANONRODE",
          style: TextStyle(
            fontWeight: FontWeight.w900,
            fontSize: 20,
            letterSpacing: 1.2,
            color: Colors.white,
          ),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined, color: Colors.white70),
            onPressed: _openServerSheet,
          ),
          AnimatedBuilder(
            animation: DownloadManager.instance,
            builder: (context, _) {
              final activeCount = DownloadManager.instance.activeTasks.length;
              return Stack(
                alignment: Alignment.center,
                children: [
                  IconButton(
                    icon: const Icon(Icons.download, color: Colors.white70),
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const DownloadsScreen()),
                      );
                    },
                  ),
                  if (activeCount > 0)
                    Positioned(
                      top: 8,
                      right: 8,
                      child: Container(
                        padding: const EdgeInsets.all(4),
                        decoration: const BoxDecoration(
                          color: Color(0xFF38BDF8),
                          shape: BoxShape.circle,
                        ),
                        child: Text(
                          "$activeCount",
                          style: const TextStyle(
                            color: Colors.black,
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                ],
              );
            },
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: Column(
        children: [
          // Search Input Bar
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: Container(
              decoration: BoxDecoration(
                color: const Color(0xFF141721),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: Colors.white.withOpacity(0.08)),
              ),
              child: Row(
                children: [
                  const SizedBox(width: 14),
                  const Icon(Icons.search, color: Colors.white54, size: 20),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: _searchController,
                      style: const TextStyle(color: Colors.white, fontSize: 15),
                      decoration: const InputDecoration(
                        hintText: "Search drama, movie, or series...",
                        hintStyle: TextStyle(color: Colors.white38, fontSize: 14),
                        border: InputBorder.none,
                      ),
                      onSubmitted: (_) => _performSearch(),
                    ),
                  ),
                  if (_searchController.text.isNotEmpty)
                    IconButton(
                      icon: const Icon(Icons.clear, color: Colors.white54, size: 18),
                      onPressed: () {
                        setState(() {
                          _searchController.clear();
                          _results.clear();
                        });
                      },
                    ),
                  IconButton(
                    icon: const Icon(Icons.arrow_forward, color: Color(0xFF38BDF8)),
                    onPressed: _performSearch,
                  ),
                ],
              ),
            ),
          ),

          // Filter Chips
          SizedBox(
            height: 38,
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              scrollDirection: Axis.horizontal,
              itemCount: _filterOptions.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final option = _filterOptions[index];
                final isSelected = _selectedFilter == option['value'];
                return ChoiceChip(
                  label: Text(option['label']!),
                  selected: isSelected,
                  labelStyle: TextStyle(
                    color: isSelected ? Colors.white : Colors.white60,
                    fontSize: 12,
                    fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                  ),
                  backgroundColor: const Color(0xFF141721),
                  selectedColor: const Color(0xFF0284C7),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                  side: BorderSide(color: isSelected ? Colors.transparent : Colors.white10),
                  onSelected: (selected) {
                    if (selected) {
                      setState(() => _selectedFilter = option['value']!);
                      if (_searchController.text.isNotEmpty) {
                        _performSearch();
                      }
                    }
                  },
                );
              },
            ),
          ),

          const SizedBox(height: 12),

          // Results List
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
                                onPressed: _performSearch,
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
                              children: const [
                                Icon(Icons.movie_creation_outlined, size: 56, color: Colors.white12),
                                SizedBox(height: 16),
                                Text(
                                  "Search any show to view episodes",
                                  style: TextStyle(color: Colors.white38, fontSize: 14),
                                ),
                              ],
                            ),
                          )
                        : ListView.builder(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                            itemCount: _results.length,
                            itemBuilder: (context, index) {
                              final show = _results[index];
                              return Container(
                                margin: const EdgeInsets.only(bottom: 10),
                                decoration: BoxDecoration(
                                  color: const Color(0xFF141721),
                                  borderRadius: BorderRadius.circular(14),
                                  border: Border.all(color: Colors.white.withOpacity(0.06)),
                                ),
                                child: ListTile(
                                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                                  title: Text(
                                    show.title,
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                      fontSize: 15,
                                    ),
                                  ),
                                  subtitle: Padding(
                                    padding: const EdgeInsets.only(top: 6),
                                    child: Row(
                                      children: [
                                        Container(
                                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                          decoration: BoxDecoration(
                                            color: const Color(0xFF1E293B),
                                            borderRadius: BorderRadius.circular(6),
                                          ),
                                          child: Text(
                                            show.site.toUpperCase(),
                                            style: const TextStyle(
                                              fontSize: 11,
                                              fontWeight: FontWeight.w600,
                                              color: Color(0xFF38BDF8),
                                            ),
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                  trailing: const Icon(Icons.arrow_forward_ios, size: 14, color: Colors.white38),
                                  onTap: () => _openEpisodeSheet(show),
                                ),
                              );
                            },
                          ),
          ),
        ],
      ),
    );
  }
}
