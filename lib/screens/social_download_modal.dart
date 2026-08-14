import 'package:flutter/material.dart';
import '../config/app_config.dart';
import '../services/api_service.dart';
import '../services/download_manager.dart';

class SocialDownloadModal extends StatefulWidget {
  final String url;

  const SocialDownloadModal({super.key, required this.url});

  @override
  State<SocialDownloadModal> createState() => _SocialDownloadModalState();
}

class _SocialDownloadModalState extends State<SocialDownloadModal> {
  bool _isLoading = true;
  String? _error;
  String _title = "Social Video";
  String _format = "video"; // 'video' or 'audio'

  @override
  void initState() {
    super.initState();
    _fetchDetails();
  }

  Future<void> _fetchDetails() async {
    try {
      final recipe = await ApiService.resolveEpisode(widget.url);
      if (mounted) {
        setState(() {
          _title = recipe['title'] ?? (widget.url.contains('instagram.com') ? 'Instagram Media' : 'Online Video');
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _title = widget.url.contains('instagram.com') ? 'Instagram Reel' : 'Online Media';
          _isLoading = false;
        });
      }
    }
  }

  void _startDownload() {
    DownloadManager.instance.enqueueDirectMedia(
      title: _title,
      originalUrl: widget.url,
      format: _format,
    );
    Navigator.of(context).pop();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text("Downloading $_title (${_format == 'audio' ? 'MP3' : 'Video'})..."),
        backgroundColor: const Color(0xFF141722),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
      decoration: const BoxDecoration(
        color: Color(0xFF141722),
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Drag Handle
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.white24,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 18),

          // Header
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: const Color(0xFF00E5FF).withOpacity(0.12),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: const Icon(Icons.flash_on_rounded, color: Color(0xFF00E5FF), size: 22),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _isLoading ? "Resolving link..." : _title,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 3),
                    Text(
                      widget.url,
                      style: const TextStyle(fontSize: 12, color: Colors.white54),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),

          if (_isLoading)
            const Center(
              child: Padding(
                padding: EdgeInsets.symmetric(vertical: 24),
                child: CircularProgressIndicator(color: Color(0xFF00E5FF)),
              ),
            )
          else ...[
            // Format Options
            InkWell(
              onTap: () => setState(() => _format = "video"),
              borderRadius: BorderRadius.circular(16),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                decoration: BoxDecoration(
                  color: _format == "video" ? const Color(0xFF00E5FF).withOpacity(0.1) : const Color(0xFF1E2333),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color: _format == "video" ? const Color(0xFF00E5FF) : Colors.white10,
                    width: 1.5,
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.video_library_rounded,
                      color: _format == "video" ? const Color(0xFF00E5FF) : Colors.white70,
                      size: 20,
                    ),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text("Full Video (Best Quality)", style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                          Text("MP4 • Optimized up to 720p/1080p", style: TextStyle(color: Colors.white54, fontSize: 12)),
                        ],
                      ),
                    ),
                    if (_format == "video")
                      const Icon(Icons.check_circle_rounded, color: Color(0xFF00E5FF), size: 20),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 10),

            InkWell(
              onTap: () => setState(() => _format = "audio"),
              borderRadius: BorderRadius.circular(16),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                decoration: BoxDecoration(
                  color: _format == "audio" ? const Color(0xFF00E5FF).withOpacity(0.1) : const Color(0xFF1E2333),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color: _format == "audio" ? const Color(0xFF00E5FF) : Colors.white10,
                    width: 1.5,
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.music_note_rounded,
                      color: _format == "audio" ? const Color(0xFF00E5FF) : Colors.white70,
                      size: 20,
                    ),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text("Audio Only", style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                          Text("MP3 • Ideal for OSTs & Songs", style: TextStyle(color: Colors.white54, fontSize: 12)),
                        ],
                      ),
                    ),
                    if (_format == "audio")
                      const Icon(Icons.check_circle_rounded, color: Color(0xFF00E5FF), size: 20),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Action Button
            ElevatedButton(
              onPressed: _startDownload,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF00E5FF),
                foregroundColor: Colors.black,
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
                elevation: 0,
              ),
              child: const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.bolt_rounded, size: 20),
                  SizedBox(width: 8),
                  Text(
                    "Download to Anon Folder",
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}
