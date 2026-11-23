package com.musicamp.poweramp

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 100% Pixel-Perfect Poweramp Library Screen (`02_library.png`, `15_library_scrolled.png`) &
 * Category Drill-Down Screen (`09_all_songs.png`).
 */
@Composable
fun PowerampLibraryScreen(
    controller: PowerampController,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PowerampBg)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = controller.activeLibraryCategory to controller.activeSubFilter,
                transitionSpec = {
                    val isDrillDown = targetState.first != null && initialState.first == null ||
                        (targetState.second != null && initialState.second == null)
                    val dir = if (isDrillDown) 1 else -1
                    (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { dir * it / 4 } +
                        fadeIn(tween(220)) +
                        scaleIn(initialScale = if (isDrillDown) 0.94f else 1.04f, animationSpec = tween(260)))
                        .togetherWith(
                            slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -dir * it / 4 } +
                                fadeOut(tween(180)) +
                                scaleOut(targetScale = if (isDrillDown) 1.04f else 0.94f, animationSpec = tween(220))
                        )
                },
                label = "libraryCategoryDrillDown"
            ) { (activeCategory, _) ->
                if (activeCategory == null) {
                    LibraryCategoriesList(controller)
                } else {
                    LibraryCategoryDetailView(controller, activeCategory)
                }
            }
        }

        PowerampBottomDock(
            controller = controller,
            showMiniPlayer = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryCategoriesList(controller: PowerampController) {
    val categories = remember { LibraryCategory.entries }
    // Snapshot tracks cleanly so scrolling never recomputes stats/groupings on every frame
    val libraryTracks = controller.allTracks.toList()
    val trackCount = libraryTracks.size
    val (albumCount, artistCount, artistList, genreList) = remember(libraryTracks) {
        val albums = libraryTracks.map { it.album }.distinct().size
        val artists = libraryTracks.map { it.artist }.distinct().size
        val topArtists = libraryTracks.groupBy { it.artist }.entries
            .sortedByDescending { it.value.size }
            .take(10)
            .map { e -> e.key to "${e.value.size} ${if (e.value.size == 1) "song" else "songs"}" }
        val topGenres = libraryTracks.groupBy { it.genre }.entries
            .sortedWith(compareBy({ it.key == "Unknown genre" }, { -it.value.size }))
            .take(8)
            .map { it.key }
        LibraryHomeStats(albums, artists, topArtists, topGenres)
    }

    PullToRefreshBox(
        isRefreshing = controller.scanning,
        onRefresh = { controller.refreshLibrary(showFeedback = true) },
        modifier = Modifier
            .fillMaxSize()
            .background(PowerampBg)
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PowerampBg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. TOP CURVED BERRY HEADER WITH SEARCH BAR & FEATURED ALBUM CAROUSEL
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(ThemeBerryHeaderGradient)
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Search Pill Bar (White rounded pill with Search & Mic)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(if (AppTheme.isDark) PowerampPillBg else Color.White)
                            .clickable {
                                controller.currentMainTab = MainNavTab.SEARCH
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF9E9E9E),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Search",
                            color = Color(0xFF9E9E9E),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Voice Search",
                            tint = Color(0xFF9E9E9E),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // "Albums" Section Header + "3 New" Badge + Settings Quick Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Albums",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            // "3 New" Pill Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$albumCount",
                                    color = ThemeBerryPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // Refresh Library + Settings Icon Buttons in Header
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LibraryRefreshIconButton(
                                isScanning = controller.scanning,
                                onClick = { controller.refreshLibrary(showFeedback = true) },
                                containerColor = Color(0x33FFFFFF),
                                iconTint = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF))
                                    .clickable { controller.showSettingsScreen = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Featured card: library summary + "Play Random" button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(ThemeCardColors[1])
                            .clickable {
                                val randomTrack = controller.allTracks.shuffled().firstOrNull() ?: controller.currentTrack
                                if (randomTrack != null) {
                                    controller.playTrack(randomTrack)
                                } else {
                                    controller.togglePlayPause()
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = ThemeCardColors
                                )
                            )
                            // Ambient concert lighting
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(ThemeBerryLight.copy(alpha = 0.55f), Color.Transparent),
                                    center = Offset(w * 0.72f, h * 0.45f),
                                    radius = w * 0.45f
                                )
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Play Random",
                                color = Color(0xCCFFFFFF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = if (trackCount == 0) "No music found" else "$trackCount ${if (trackCount == 1) "song" else "songs"}",
                                        color = Color.White,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (trackCount == 0) "Allow audio access or add music to this device"
                                        else "$albumCount ${if (albumCount == 1) "album" else "albums"}  |  " +
                                            "$artistCount ${if (artistCount == 1) "artist" else "artists"}",
                                        color = Color(0xB3FFFFFF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Circular White Play Button with Berry Play Icon
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable {
                                            val randomTrack = controller.allTracks.shuffled().firstOrNull() ?: controller.currentTrack
                                            if (randomTrack != null) {
                                                controller.playTrack(randomTrack)
                                            } else {
                                                controller.togglePlayPause()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = ThemeBerryPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        // 2. "ARTISTS" SECTION WITH "SEE ALL"
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Artists",
                        color = PowerampTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "See All",
                        color = ThemeBerryPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            controller.activeSubFilter = null
                            controller.activeLibraryCategory = LibraryCategory.ARTISTS
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Top artists from the real library (by number of songs)
                val berryShades = ThemeTileShades

                if (artistList.isEmpty()) {
                    Text(text = "No artists yet", color = PowerampTextSecondary, fontSize = 13.sp)
                } else LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        items = artistList,
                        key = { it.first },
                        contentType = { "artistTile" }
                    ) { (name, songs) ->
                        val artColor = berryShades[(name.hashCode() and 0x7fffffff) % berryShades.size]
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.clickable {
                                controller.activeSubFilter = name
                                controller.activeLibraryCategory = LibraryCategory.ARTISTS
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(artColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name.take(2).uppercase(),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = name,
                                color = PowerampTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = songs,
                                color = PowerampTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 3. "GENRES" SECTION WITH "SEE ALL"
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Genres",
                        color = PowerampTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "See All",
                        color = ThemeBerryPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            controller.activeSubFilter = null
                            controller.activeLibraryCategory = LibraryCategory.GENRES
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val genrePalette = ThemeTileShades.let { s -> listOf(listOf(s[0], s[1]), listOf(s[1], s[2]), listOf(s[2], s[0])) }

                if (genreList.isEmpty()) {
                    Text(text = "No genres yet", color = PowerampTextSecondary, fontSize = 13.sp)
                } else LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        items = genreList,
                        key = { it },
                        contentType = { "genreTile" }
                    ) { genreName ->
                        val brush = Brush.linearGradient(genrePalette[(genreName.hashCode() and 0x7fffffff) % genrePalette.size])
                        Box(
                            modifier = Modifier
                                .width(135.dp)
                                .height(82.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(brush)
                                .clickable {
                                    controller.activeSubFilter = genreName
                                    controller.activeLibraryCategory = LibraryCategory.GENRES
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(
                                text = genreName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. QUICK CATEGORIES SHORTCUTS
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp)
            ) {
                Text(
                    text = "Library Folders & Categories",
                    color = PowerampTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Prominent Settings Entry Row in Library List
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clickable { controller.showSettingsScreen = true }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ThemeBerryPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Settings",
                        color = PowerampTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Audio engine, DSP, Look and feel, Library scan",
                        color = PowerampTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        items(
            items = categories,
            key = { it.name },
            contentType = { "libraryCategory" }
        ) { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clickable {
                        controller.activeSubFilter = null
                        controller.isSelectionMode = false
                        controller.selectedTrackIds.clear()
                        controller.activeLibraryCategory = category
                    }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibraryCategoryBadge(category = category, size = 36.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = category.title,
                    color = PowerampTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                if (category == LibraryCategory.QUEUE) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (controller.queueTracks.isEmpty()) "empty" else "${controller.queueTracks.size}",
                        color = PowerampTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    }
}

private data class LibraryHomeStats(
    val albumCount: Int,
    val artistCount: Int,
    val topArtists: List<Pair<String, String>>,
    val topGenres: List<String>
)

@Composable
private fun LibraryRefreshIconButton(
    isScanning: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    iconTint: Color,
    borderColor: Color? = null
) {
    val rotation = if (isScanning) {
        val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = LinearEasing),
                repeatMode = AnimRepeatMode.Restart
            ),
            label = "refreshAngle"
        ).value
    } else 0f

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(containerColor)
            .then(if (borderColor != null) Modifier.border(1.2.dp, borderColor, CircleShape) else Modifier)
            .clickable(enabled = !isScanning, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Refresh Library",
            tint = iconTint,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = rotation }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LibraryCategoryDetailView(
    controller: PowerampController,
    category: LibraryCategory
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeAlphabetLetter by remember { mutableStateOf<Char?>(null) }

    // Determine whether we should show a grouping list first (e.g. Folders, Albums, Artists, Genres, Years, Playlists)
    // unless activeSubFilter is already chosen
    val subFilter = controller.activeSubFilter
    val tracksSnapshot = controller.allTracks.toList()
    val playlistsSnapshot = controller.playlists.toList()
    val queueSnapshot = controller.queueTracks.toList()

    val groupingItems: List<Pair<String, String>>? = remember(category, subFilter, tracksSnapshot, playlistsSnapshot) {
        if (subFilter == null) {
            when (category) {
                LibraryCategory.FOLDERS, LibraryCategory.FOLDERS_HIERARCHY -> {
                    tracksSnapshot.groupBy { it.folderName }
                        .map { (folder, tracks) -> folder to "${tracks.size} songs | ${tracks.first().folderPath}" }
                        .sortedBy { it.first.lowercase() }
                }
                LibraryCategory.ALBUMS -> {
                    tracksSnapshot.groupBy { it.album }
                        .map { (album, tracks) -> album to "${tracks.first().artist} • ${tracks.size} songs" }
                        .sortedBy { it.first.lowercase() }
                }
                LibraryCategory.ARTISTS, LibraryCategory.ALBUM_ARTISTS -> {
                    tracksSnapshot.groupBy { it.artist }
                        .map { (artist, tracks) -> artist to "${tracks.size} songs" }
                        .sortedBy { it.first.lowercase() }
                }
                LibraryCategory.GENRES -> {
                    tracksSnapshot.groupBy { it.genre }
                        .map { (genre, tracks) -> genre to "${tracks.size} songs" }
                        .sortedBy { it.first.lowercase() }
                }
                LibraryCategory.YEARS -> {
                    tracksSnapshot.groupBy { yearLabel(it.year) }
                        .map { (yr, tracks) -> yr to "${tracks.size} songs" }
                        .sortedByDescending { it.first }
                }
                LibraryCategory.COMPOSERS -> {
                    tracksSnapshot.groupBy { it.composer }
                        .map { (comp, tracks) -> comp to "${tracks.size} songs" }
                        .sortedBy { it.first.lowercase() }
                }
                LibraryCategory.PLAYLISTS -> {
                    playlistsSnapshot.map { pl -> pl.name to "${pl.trackIds.size} songs" }
                }
                else -> null
            }
        } else null
    }

    val filteredTracks: List<AudioTrack> = remember(category, subFilter, tracksSnapshot, playlistsSnapshot, queueSnapshot) {
        when (category) {
            LibraryCategory.ALL_SONGS -> tracksSnapshot
            LibraryCategory.FOLDERS, LibraryCategory.FOLDERS_HIERARCHY ->
                tracksSnapshot.filter { subFilter == null || it.folderName == subFilter }
            LibraryCategory.ALBUMS ->
                tracksSnapshot.filter { subFilter == null || it.album == subFilter }
            LibraryCategory.ARTISTS, LibraryCategory.ALBUM_ARTISTS ->
                tracksSnapshot.filter { subFilter == null || it.artist == subFilter }
            LibraryCategory.GENRES ->
                tracksSnapshot.filter { subFilter == null || it.genre == subFilter }
            LibraryCategory.YEARS ->
                tracksSnapshot.filter { subFilter == null || yearLabel(it.year) == subFilter }
            LibraryCategory.COMPOSERS ->
                tracksSnapshot.filter { subFilter == null || it.composer == subFilter }
            LibraryCategory.PLAYLISTS -> {
                val pl = playlistsSnapshot.find { it.name == subFilter }
                if (pl != null) tracksSnapshot.filter { it.id in pl.trackIds } else tracksSnapshot
            }
            LibraryCategory.QUEUE -> queueSnapshot
            LibraryCategory.MOST_PLAYED -> tracksSnapshot.sortedByDescending { it.playCount }
            LibraryCategory.TOP_RATED -> tracksSnapshot.filter { it.rating == 1 }
            LibraryCategory.LOW_RATED -> tracksSnapshot.filter { it.rating == -1 }
            LibraryCategory.RECENTLY_PLAYED -> tracksSnapshot.filter { it.lastPlayedTimestamp > 0 }.sortedByDescending { it.lastPlayedTimestamp }
            LibraryCategory.RECENTLY_ADDED -> tracksSnapshot.sortedByDescending { it.dateAddedSec }
            LibraryCategory.LONG -> tracksSnapshot.filter { it.durationMs >= 60_000L }
            LibraryCategory.STREAMS, LibraryCategory.BOOKMARKS -> emptyList()
        }
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        PullToRefreshBox(
            isRefreshing = controller.scanning,
            onRefresh = { controller.refreshLibrary(showFeedback = true) },
            modifier = Modifier.fillMaxSize()
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 24.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            // 1. Back Button Pill (`↑ Library` or `↑ Category`) + Refresh Library Button
            item(key = "detail_back_row", contentType = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(18.dp))
                            .clickable {
                                if (controller.activeSubFilter != null) {
                                    controller.activeSubFilter = null
                                } else {
                                    controller.activeLibraryCategory = null
                                }
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.SubdirectoryArrowLeft,
                                contentDescription = "Back",
                                tint = PowerampTextPrimary,
                                modifier = Modifier
                                    .size(17.dp)
                                    .rotate(90f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (subFilter != null) category.title else "Library",
                                color = PowerampTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    LibraryRefreshIconButton(
                        isScanning = controller.scanning,
                        onClick = { controller.refreshLibrary(showFeedback = true) },
                        containerColor = Color.Transparent,
                        iconTint = PowerampTextPrimary,
                        borderColor = PowerampBorderGray
                    )
                }
            }

            // 2. Large Category Icon + Title Row (`09_all_songs.png`)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LibraryCategoryBadge(category = category, size = 48.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = subFilter ?: category.title,
                        color = PowerampTextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 3. Action Buttons Toolbar Row: Shuffle, Play, Search, Select (left) + 3-dot Menu (right) (`09_all_songs.png`)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Shuffle outlined pill
                        Box(
                            modifier = Modifier
                                .size(width = 52.dp, height = 38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(19.dp))
                                .clickable {
                                    controller.shuffleMode = ShuffleMode.JUST_SONGS
                                    filteredTracks.randomOrNull()?.let { controller.playTrack(it, filteredTracks) }
                                    controller.showToast("Shuffling ${subFilter ?: category.title}")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = "Shuffle",
                                tint = PowerampTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Play outlined pill
                        Box(
                            modifier = Modifier
                                .size(width = 52.dp, height = 38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(19.dp))
                                .clickable {
                                    filteredTracks.firstOrNull()?.let { controller.playTrack(it, filteredTracks) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = PowerampTextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Search outlined pill
                        Box(
                            modifier = Modifier
                                .size(width = 52.dp, height = 38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(19.dp))
                                .clickable {
                                    controller.currentMainTab = MainNavTab.SEARCH
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = PowerampTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Select outlined pill
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(if (controller.isSelectionMode) Color(0xFF2B2D32) else Color.Transparent)
                                .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(19.dp))
                                .clickable {
                                    controller.isSelectionMode = !controller.isSelectionMode
                                    if (!controller.isSelectionMode) {
                                        controller.selectedTrackIds.clear()
                                    }
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (controller.isSelectionMode) "Done (${controller.selectedTrackIds.size})" else "Select",
                                color = if (controller.isSelectionMode) Color.White else PowerampTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // 3-dot Menu circle button (or Add button for Playlists / Streams)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.2.dp, PowerampBorderGray, CircleShape)
                            .clickable {
                                when (category) {
                                    LibraryCategory.STREAMS -> controller.showAddStreamDialog = true
                                    LibraryCategory.PLAYLISTS -> controller.showAddToPlaylistDialog = true
                                    LibraryCategory.QUEUE -> {
                                        controller.queueTracks.clear()
                                        controller.showToast("Queue Cleared")
                                    }
                                    else -> {
                                        controller.refreshLibrary(showFeedback = true)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (category == LibraryCategory.STREAMS || (category == LibraryCategory.PLAYLISTS && subFilter == null)) {
                                Icons.Filled.Add
                            } else {
                                Icons.Filled.MoreVert
                            },
                            contentDescription = "Menu",
                            tint = PowerampTextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // If Selection Mode is active, display batch action bar
                if (controller.isSelectionMode && controller.selectedTrackIds.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E2024))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${controller.selectedTrackIds.size} selected",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "+ Queue",
                                color = PowerampNeonGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    val selected = controller.allTracks.filter { it.id in controller.selectedTrackIds }
                                    controller.queueTracks.addAll(selected)
                                    controller.isSelectionMode = false
                                    controller.selectedTrackIds.clear()
                                    controller.showToast("Added ${selected.size} tracks to Queue")
                                }
                            )
                            Text(
                                text = "Bookmark",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    val selected = controller.allTracks.filter { it.id in controller.selectedTrackIds }
                                    selected.forEach { controller.addBookmarkForTrack(it) }
                                    controller.isSelectionMode = false
                                    controller.selectedTrackIds.clear()
                                }
                            )
                        }
                    }
                }
            }

            // 4. Special Views for Bookmarks, Streams, or Grouping Folders/Albums/Artists
            if (category == LibraryCategory.BOOKMARKS) {
                if (controller.bookmarks.isEmpty()) {
                    item {
                        EmptyListPlaceholder(
                            title = "No bookmarks yet",
                            subtitle = "Open track menu (⋮) -> Bookmark to save track positions"
                        )
                    }
                } else {
                    items(controller.bookmarks) { bm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(PowerampSurface)
                                .clickable {
                                    val target = controller.allTracks.find { it.id == bm.trackId }
                                    if (target != null) {
                                        controller.playTrack(target)
                                        controller.seekToMs(bm.positionMs)
                                    }
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackThumb(controller, controller.allTracks.find { it.id == bm.trackId }, 56.dp)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bm.trackTitle,
                                    color = PowerampTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = bm.artist,
                                    color = PowerampTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Position: ${bm.formattedPosition}",
                                    color = PowerampNeonGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete Bookmark",
                                tint = Color(0xFF8C8E93),
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { controller.bookmarks.remove(bm) }
                            )
                        }
                    }
                }
            } else if (category == LibraryCategory.STREAMS) {
                items(controller.streams) { stream ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(PowerampSurface)
                            .clickable {
                                controller.showToast("Streams are not supported yet", stream.title)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LibraryCategoryBadge(category = LibraryCategory.STREAMS, size = 48.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stream.title,
                                color = PowerampTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = stream.url,
                                color = PowerampTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else if (groupingItems != null) {
                items(
                    items = groupingItems,
                    key = { it.first },
                    contentType = { "groupRow" }
                ) { (groupTitle, groupSub) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(PowerampSurface)
                            .clickable { controller.activeSubFilter = groupTitle }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LibraryCategoryBadge(category = category, size = 46.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = groupTitle,
                                color = PowerampTextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = groupSub,
                                color = PowerampTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else if (filteredTracks.isEmpty()) {
                item {
                    EmptyListPlaceholder(
                        title = "Doesn't look like anything to me",
                        subtitle = "No matching tracks in ${subFilter ?: category.title}"
                    )
                }
            } else {
                // 5. Track Rows matching `09_all_songs.png`
                items(
                    items = filteredTracks,
                    key = { it.id },
                    contentType = { "trackRow" }
                ) { track ->
                    val isCurrent = controller.currentTrack?.id == track.id
                    val isSelected = track.id in controller.selectedTrackIds

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                when {
                                    isSelected -> if (AppTheme.isDark) ThemeBerryPrimary.copy(alpha = 0.28f) else Color(0xFFEFD9E4)
                                    isCurrent -> if (AppTheme.isDark) ThemeBerryPrimary.copy(alpha = 0.16f) else Color(0xFFF7E4EE)
                                    else -> Color.Transparent
                                }
                            )
                            .combinedClickable(
                                onClick = {
                                    if (controller.isSelectionMode) {
                                        if (isSelected) controller.selectedTrackIds.remove(track.id)
                                        else controller.selectedTrackIds.add(track.id)
                                    } else {
                                        controller.playTrack(track, filteredTracks)
                                    }
                                },
                                onLongClick = {
                                    controller.actionTargetTrack = track
                                    controller.showTrackActionDialog = true
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (controller.isSelectionMode) {
                            Icon(
                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = "Select",
                                tint = if (isSelected) PowerampNeonGreen else Color(0xFF8C8E93),
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(22.dp)
                            )
                        }
                        // Real cover art (or a neutral placeholder when the file has none)
                        TrackThumb(controller, track, 72.dp)

                        Spacer(modifier = Modifier.width(12.dp))

                        // Track Title, Artist, and Treble Clef Meta Line (`𝄞 1:58 | mp3`)
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = track.title,
                                color = PowerampTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                color = PowerampTextSecondary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "𝄞 ",
                                    color = Color(0xFF8E9095),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = track.metaLine,
                                    color = Color(0xFF9EA0A5),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        // Right-edge Interactive A-Z Fast Scroller with Big Alphabet Bubble Preview
        PowerampAlphabetScroller(
            onLetterSelected = { letter, isDragging ->
                val labels: List<String> = groupingItems?.map { it.first } ?: filteredTracks.map { it.title }
                val targetIndex = when (letter) {
                    '˄' -> -3 // scroll all the way to top header (index 0)
                    '0' -> labels.indexOfFirst { it.firstOrNull()?.isDigit() == true }
                    '#' -> labels.indexOfFirst { ch -> ch.firstOrNull()?.let { !it.isLetterOrDigit() } == true }
                        .takeIf { it >= 0 } ?: (labels.lastIndex)
                    else -> {
                        val exact = labels.indexOfFirst { it.firstOrNull()?.uppercaseChar() == letter }
                        if (exact >= 0) exact
                        else labels.indexOfFirst { (it.firstOrNull()?.uppercaseChar() ?: ' ') >= letter }
                    }
                }
                if (targetIndex == -3) {
                    scope.launch {
                        if (isDragging) listState.scrollToItem(0) else listState.animateScrollToItem(0)
                    }
                } else if (targetIndex >= 0) {
                    scope.launch {
                        val listIdx = targetIndex + 3
                        if (isDragging) listState.scrollToItem(listIdx) else listState.animateScrollToItem(listIdx)
                    }
                }
            },
            onActiveLetterChanged = { letter, _ ->
                activeAlphabetLetter = letter
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.88f)
                .padding(end = 1.dp)
        )

        // Large Floating Alphabet Overlay Bubble (pops up smoothly when tapping/dragging A-Z strip)
        PowerampAlphabetBubble(
            letter = activeAlphabetLetter,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(end = 12.dp)
        )
    }
}

@Composable
private fun EmptyListPlaceholder(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = Color(0xFF8E9095),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            color = Color(0xFF606266),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 100% Pixel-Perfect Poweramp Search Screen (`04_search.png`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PowerampSearchScreen(
    controller: PowerampController,
    modifier: Modifier = Modifier
) {
    val query = controller.searchQuery.trim()
    val allTracksSnapshot = controller.allTracks.toList()
    val results = remember(query, allTracksSnapshot) {
        if (query.isEmpty()) {
            allTracksSnapshot
        } else {
            allTracksSnapshot.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true) ||
                    it.folderName.contains(query, ignoreCase = true) ||
                    it.genre.contains(query, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PowerampBg)
            .statusBarsPadding()
    ) {
        // 1. Rounded Search Input Bar (`04_search.png`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(PowerampPillBg)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (controller.searchQuery.isEmpty()) {
                    Text(
                        text = "Search",
                        color = Color(0xFF9EA0A5),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                BasicTextField(
                    value = controller.searchQuery,
                    onValueChange = { controller.searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    cursorBrush = SolidColor(PowerampNeonGreen),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear",
                tint = Color(0xFFD0D2D6),
                modifier = Modifier
                    .size(22.dp)
                    .clickable { controller.searchQuery = "" }
            )
        }

        // 2. Horizontally Scrollable Filter Chips (`All`, `Albums`, `Artists`, `Album Artists`, `Folders`)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(controller.searchFilterOptions) { chip ->
                val selected = controller.searchFilterChip == chip
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Color(0xFF33353A) else Color(0xFF1D1E21))
                        .clickable { controller.searchFilterChip = chip }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chip,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 3. Action Row: Shuffle, Play, Select (left) + 3-dot Menu (right) (`04_search.png`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val hasResults = results.isNotEmpty()
                val tintColor = if (hasResults) Color.White else Color(0xFF424347)

                Box(
                    modifier = Modifier
                        .size(width = 50.dp, height = 36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, PowerampBorderGray, RoundedCornerShape(18.dp))
                        .clickable(enabled = hasResults) {
                            results.randomOrNull()?.let { controller.playTrack(it, results) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = tintColor,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(width = 50.dp, height = 36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, PowerampBorderGray, RoundedCornerShape(18.dp))
                        .clickable(enabled = hasResults) {
                            results.firstOrNull()?.let { controller.playTrack(it, results) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = tintColor,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, PowerampBorderGray, RoundedCornerShape(18.dp))
                        .clickable(enabled = hasResults) {
                            controller.isSelectionMode = !controller.isSelectionMode
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select",
                        color = tintColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(1.2.dp, PowerampBorderGray, CircleShape)
                    .clickable { controller.searchQuery = "" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // 4. Search Results List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = results,
                key = { it.id },
                contentType = { "searchTrackRow" }
            ) { track ->
                val isCurrent = controller.currentTrack?.id == track.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isCurrent) Color(0xFF232427) else Color.Transparent)
                        .combinedClickable(
                            onClick = { controller.playTrack(track, results) },
                            onLongClick = {
                                controller.actionTargetTrack = track
                                controller.showTrackActionDialog = true
                            }
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackThumb(controller, track, 60.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            color = Color(0xFFD8DADF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "𝄞 ${track.metaLine}",
                            color = PowerampTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        PowerampBottomDock(
            controller = controller,
            showMiniPlayer = true
        )
    }
}

/**
 * 100% Pixel-Perfect Poweramp Lyrics View Screen (`13_lyrics.png`).
 */
@Composable
fun PowerampLyricsScreen(
    controller: PowerampController,
    modifier: Modifier = Modifier
) {
    val track = controller.actionTargetTrack ?: controller.currentTrack
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PowerampBg)
    ) {
        // Top Header: Small Album Art, Track Title, Artist, Meta Line (`𝄞 1:12 | mp3`), 3-dot Menu Circle (`13_lyrics.png`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PowerampMountainArtwork(
                modifier = Modifier
                    .size(64.dp)
                    .clickable { controller.showLyricsScreen = false }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "No track",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track?.artist ?: "Unknown artist",
                    color = Color(0xFFE2E4E8),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "𝄞 ${track?.metaLine ?: ""}",
                    color = PowerampTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.2.dp, PowerampBorderGray, CircleShape)
                    .clickable {
                        controller.actionTargetTrack = track
                        controller.showEditTagsDialog = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Lyrics Options",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Center Lyrics Area (`13_lyrics.png`)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            val customLyrics = track?.lyrics?.takeIf { it.isNotBlank() }
            if (customLyrics == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No lyrics found",
                        color = Color(0xFF9EA0A5),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lyrics can be searched in 3rd party app",
                        color = Color(0xFFB5B7BC),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(19.dp))
                            .clickable {
                                controller.actionTargetTrack = track
                                controller.showEditTagsDialog = true
                            }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Open lyrics app",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(26.dp))
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Lyrics Settings",
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable {
                                controller.showSettingsScreen = true
                            }
                    )
                }
            } else {
                LazyColumn(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(customLyrics.lines()) { line ->
                        Text(
                            text = line,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }

        PowerampBottomDock(
            controller = controller,
            showMiniPlayer = true
        )
    }
}
