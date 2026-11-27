package com.musicamp.poweramp

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 1. Main Menu Sheet (`05_main_menu.png`) anchored right above the bottom dock.
 */
@Composable
private fun PowerampMainMenuOverlayContent(controller: PowerampController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable { controller.showMainMenuSheet = false }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 100.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(PowerampDialogBg)
                .clickable(enabled = false) {}
                .padding(vertical = 18.dp, horizontal = 24.dp)
        ) {
            // POWERAMP Full Version Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "MUSICAMP",
                    color = ThemeBerryPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black.appWeight,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Full Version",
                    color = PowerampTextSecondary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold.appWeight
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Settings item
            MainMenuRowItem(
                icon = Icons.Filled.Settings,
                label = "Settings",
                onClick = {
                    controller.showMainMenuSheet = false
                    controller.showSettingsScreen = true
                }
            )

            // Equalizer item
            MainMenuRowItem(
                icon = Icons.Filled.Tune,
                label = "Equalizer & Audio DSP",
                onClick = {
                    controller.showMainMenuSheet = false
                    controller.currentMainTab = MainNavTab.EQUALIZER
                }
            )

            // Sleep Timer item
            MainMenuRowItem(
                icon = Icons.Filled.Schedule,
                label = "Sleep Timer",
                onClick = {
                    controller.showMainMenuSheet = false
                    controller.showSleepTimerDialog = true
                }
            )

            // Changelog item
            MainMenuRowItem(
                icon = Icons.Filled.ShowChart,
                label = "Changelog",
                onClick = {
                    controller.showMainMenuSheet = false
                    controller.showChangelogDialog = true
                }
            )

            // Help item
            MainMenuRowItem(
                icon = Icons.Filled.Help,
                label = "Help",
                onClick = {
                    controller.showMainMenuSheet = false
                    controller.showHelpDialog = true
                }
            )
        }
    }
}

@Composable
private fun MainMenuRowItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = ThemeBerryPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(22.dp))
        Text(
            text = label,
            color = PowerampTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold.appWeight
        )
    }
}

/**
 * 2. Track Context Menu Dialog (`11_player_menu.png`).
 */
@Composable
private fun PowerampTrackMenuDialogContent(controller: PowerampController) {
    val track = controller.actionTargetTrack ?: controller.currentTrack ?: return
    val isLiked = track.rating == 1
    val isUnliked = track.rating == -1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable { controller.showTrackActionDialog = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(PowerampDialogBg)
                .clickable(enabled = false) {}
                .padding(vertical = 18.dp, horizontal = 18.dp)
        ) {
            // Top Track Summary Row (`11_player_menu.png`)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackThumb(controller, track, 92.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = PowerampTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold.appWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        color = PowerampTextSecondary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold.appWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "𝄞 ${track.metaLine}",
                        color = Color(0xFF9094A0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold.appWeight
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, Color(0xFFDCE0E6), RoundedCornerShape(14.dp))
                                .clickable {
                                    val newR = if (isLiked) 0 else 1
                                    controller.updateTrack(track.copy(rating = newR))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ThumbUp,
                                contentDescription = "Like",
                                tint = if (isLiked) ThemeBerryPrimary else PowerampTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, Color(0xFFDCE0E6), RoundedCornerShape(14.dp))
                                .clickable {
                                    val newR = if (isUnliked) 0 else -1
                                    controller.updateTrack(track.copy(rating = newR))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ThumbDown,
                                contentDescription = "Unlike",
                                tint = if (isUnliked) Color(0xFFFF5252) else PowerampTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = PowerampBorderGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(4.dp))

            // Row 1: Delete (full width)
            TrackMenuActionButton(
                icon = Icons.Outlined.Delete,
                label = "Delete",
                modifier = Modifier.fillMaxWidth()
            ) {
                controller.showTrackActionDialog = false
                controller.deleteTrack(track)
            }

            // Row 2: Playlist (left) | Bookmark (right)
            Row(modifier = Modifier.fillMaxWidth()) {
                TrackMenuActionButton(
                    icon = Icons.Filled.Add,
                    label = "Playlist",
                    modifier = Modifier.weight(1f)
                ) {
                    controller.showTrackActionDialog = false
                    controller.showAddToPlaylistDialog = true
                }
                TrackMenuActionButton(
                    icon = Icons.Outlined.BookmarkBorder,
                    label = "Bookmark",
                    modifier = Modifier.weight(1f)
                ) {
                    controller.showTrackActionDialog = false
                    controller.addBookmarkForTrack(track)
                }
            }

            // Row 3: Album Art (full width)
            TrackMenuActionButton(
                icon = Icons.Filled.Image,
                label = "Album Art",
                modifier = Modifier.fillMaxWidth()
            ) {
                controller.showTrackActionDialog = false
                controller.showAlbumArtDialog = true
            }

            // Row 4: Info/Tags (left) | Lyrics (right)
            Row(modifier = Modifier.fillMaxWidth()) {
                TrackMenuActionButton(
                    icon = Icons.Outlined.Info,
                    label = "Info/Tags",
                    modifier = Modifier.weight(1f)
                ) {
                    controller.showTrackActionDialog = false
                    controller.showInfoTagsSheet = true
                }
                TrackMenuActionButton(
                    icon = Icons.Filled.Notes,
                    label = "Lyrics",
                    modifier = Modifier.weight(1f)
                ) {
                    controller.showTrackActionDialog = false
                    controller.showLyricsScreen = true
                }
            }
            TrackMenuActionButton(
                icon = Icons.Filled.GraphicEq,
                label = "Audio Info (Fidelity / DSP)",
                modifier = Modifier.fillMaxWidth()
            ) {
                controller.showTrackActionDialog = false
                controller.showAudioInfoDialog = true
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = PowerampBorderGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(4.dp))

            // Row 5: Artist (left) | Album (right)
            Row(modifier = Modifier.fillMaxWidth()) {
                TrackMenuActionButton(
                    icon = Icons.Filled.Mic,
                    label = "Artist",
                    modifier = Modifier.weight(1f)
                ) {
                    controller.showTrackActionDialog = false
                    controller.currentMainTab = MainNavTab.LIBRARY
                    controller.activeLibraryCategory = LibraryCategory.ARTISTS
                    controller.activeSubFilter = track.artist
                }
                TrackMenuActionButton(
                    icon = Icons.Filled.Album,
                    label = "Album",
                    modifier = Modifier.weight(1f)
                ) {
                    controller.showTrackActionDialog = false
                    controller.currentMainTab = MainNavTab.LIBRARY
                    controller.activeLibraryCategory = LibraryCategory.ALBUMS
                    controller.activeSubFilter = track.album
                }
            }

            // Row 6: Folder (left) | Genre (right)
            Row(modifier = Modifier.fillMaxWidth()) {
                TrackMenuActionButton(
                    icon = Icons.Filled.Folder,
                    label = "Folder",
                    modifier = Modifier.weight(1f)
                ) {
                    controller.showTrackActionDialog = false
                    controller.currentMainTab = MainNavTab.LIBRARY
                    controller.activeLibraryCategory = LibraryCategory.FOLDERS
                    controller.activeSubFilter = track.folderName
                }
                TrackMenuActionButton(
                    icon = Icons.Filled.MusicNote,
                    label = "Genre",
                    modifier = Modifier.weight(1f)
                ) {
                    controller.showTrackActionDialog = false
                    controller.currentMainTab = MainNavTab.LIBRARY
                    controller.activeLibraryCategory = LibraryCategory.GENRES
                    controller.activeSubFilter = track.genre
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = PowerampBorderGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(4.dp))

            // Row 7: Settings (left) | Sleep Timer (right)
            Row(modifier = Modifier.fillMaxWidth()) {
                TrackMenuActionButton(
                    icon = Icons.Filled.Settings,
                    label = "Settings",
                    modifier = Modifier.weight(1f)
                ) {
                    controller.showTrackActionDialog = false
                    controller.showSettingsScreen = true
                }
                TrackMenuActionButton(
                    icon = Icons.Filled.Schedule,
                    label = "Sleep Timer",
                    modifier = Modifier.weight(1f)
                ) {
                    controller.showTrackActionDialog = false
                    controller.showSleepTimerDialog = true
                }
            }
        }
    }
}

@Composable
private fun TrackMenuActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = ThemeBerryPrimary,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = PowerampTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold.appWeight,
            maxLines = 1
        )
    }
}

/**
 * 3. Info / Tags Bottom Sheet (`12_info_tags.png`) + Interactive Tag Editor Dialog.
 */
@Composable
private fun PowerampInfoTagsSheetContent(controller: PowerampController) {
    val track = controller.actionTargetTrack ?: controller.currentTrack ?: return
    val minutesAgo = if (track.lastPlayedTimestamp > 0) {
        ((System.currentTimeMillis() - track.lastPlayedTimestamp) / 60_000L).coerceAtLeast(1L)
    } else 6L

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable { controller.showInfoTagsSheet = false }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 16.dp)
                .fillMaxWidth()
                .height(455.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(PowerampDialogBg)
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Text(
                text = "Info/Tags",
                color = PowerampTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold.appWeight
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = track.folderPath,
                            color = ThemeBerryPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold.appWeight,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                controller.showInfoTagsSheet = false
                                controller.currentMainTab = MainNavTab.LIBRARY
                                controller.activeLibraryCategory = LibraryCategory.FOLDERS
                            }
                        )
                        Text(
                            text = track.fileName,
                            color = PowerampTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold.appWeight
                        )
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFD2D5DC), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = track.formatExt.lowercase(),
                                color = ThemeBerryPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold.appWeight
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val sec = track.durationMs / 1000L
                        Text(
                            text = "${track.formatExt.uppercase()}, $sec sec (${track.formattedDuration}), ${track.sampleRateHz} Hz, 16 bit, Stereo, ${track.bitrateKbps} kbps, ${track.fileSizeKb}kB",
                            color = PowerampTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium.appWeight
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Played Times", color = PowerampTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold.appWeight)
                            Text("${track.playCount.coerceAtLeast(1)}", color = PowerampTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold.appWeight)
                        }
                        Column {
                            Text("Last Played", color = PowerampTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold.appWeight)
                            Text("$minutesAgo min. ago", color = PowerampTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold.appWeight)
                        }
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Reset Stats",
                            tint = PowerampTextSecondary,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable {
                                    controller.updateTrack(track.copy(playCount = 0))
                                    controller.showToast("Play count cleared")
                                }
                        )
                    }
                }

                item {
                    InfoTagField("Title", track.title)
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoTagField("Track", track.trackNumber.toString(), Modifier.weight(1f))
                        InfoTagField("Disc", track.discNumber.toString(), Modifier.weight(1f))
                        InfoTagField("Year", yearLabel(track.year), Modifier.weight(1f))
                    }
                }

                item {
                    InfoTagField("Genre", track.genre)
                }

                item {
                    InfoTagField("Artist", track.artist)
                }

                item {
                    InfoTagField("Album", track.album)
                }

                item {
                    InfoTagField("Album Artist", track.albumArtist)
                }

                item {
                    InfoTagField("Composer", track.composer)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Action Row: Search & Edit Tags Pill Button (`12_info_tags.png`)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search",
                    color = ThemeBerryPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold.appWeight,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            controller.showInfoTagsSheet = false
                            controller.searchQuery = track.title.substringBeforeLast(".")
                            controller.currentMainTab = MainNavTab.SEARCH
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ThemeBerryPrimary)
                        .clickable {
                            controller.showInfoTagsSheet = false
                            controller.showEditTagsDialog = true
                        }
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Edit Tags",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold.appWeight
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoTagField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 2.dp)) {
        Text(text = label, color = PowerampTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold.appWeight)
        if (value.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, color = PowerampTextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold.appWeight)
        }
    }
}

/**
 * Edit Tags Modal Dialog allowing full editing of Title, Artist, Album, Genre, Year, and Lyrics.
 */
@Composable
private fun PowerampEditTagsDialogContent(controller: PowerampController) {
    val track = controller.actionTargetTrack ?: controller.currentTrack ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var genre by remember(track.id) { mutableStateOf(track.genre) }
    var yearStr by remember(track.id) { mutableStateOf(if (track.year > 0) track.year.toString() else "") }
    var lyrics by remember(track.id) { mutableStateOf(track.lyrics) }
    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Pending edit lives in the controller so a rotation while the system dialog is open doesn't lose it
        val pending = controller.pendingTagSave
        controller.pendingTagSave = null
        if (result.resultCode == Activity.RESULT_OK && pending != null) {
            controller.saveTrackTagsToFile(pending)
            controller.showEditTagsDialog = false
        } else {
            controller.showToast("Cancelled", "File was not modified")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable { controller.showEditTagsDialog = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(PowerampDialogBg)
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Text(
                text = "Edit Tags",
                color = PowerampTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold.appWeight
            )
            Spacer(modifier = Modifier.height(12.dp))

            TagEditInput("Title", title) { title = it }
            TagEditInput("Artist", artist) { artist = it }
            TagEditInput("Album", album) { album = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    TagEditInput("Genre", genre) { genre = it }
                }
                Box(modifier = Modifier.width(95.dp)) {
                    TagEditInput("Year", yearStr) { yearStr = it }
                }
            }
            TagEditInput("Lyrics", lyrics) { lyrics = it }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { controller.showEditTagsDialog = false }) {
                    Text("Cancel", color = PowerampTextSecondary, fontWeight = FontWeight.Bold.appWeight)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(ThemeBerryPrimary)
                        .clickable {
                            val updated = track.copy(
                                title = title.ifBlank { track.title },
                                artist = artist.ifBlank { "Unknown artist" },
                                album = album.ifBlank { "Music" },
                                genre = genre.ifBlank { "Electronic" },
                                year = yearStr.toIntOrNull() ?: track.year,
                                lyrics = lyrics
                            )
                            if (!controller.needsFileWrite(updated)) {
                                // Saved in the app (CUE, unsupported format, nothing file-side changed, or file in use)
                                controller.showEditTagsDialog = false
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                track.uri != null &&
                                track.uri.scheme == "content"
                            ) {
                                try {
                                    val pi = MediaStore.createWriteRequest(context.contentResolver, listOf(track.uri))
                                    controller.pendingTagSave = updated
                                    writePermissionLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                                } catch (_: Exception) {
                                    controller.saveTrackTagsToFile(updated)
                                    controller.showEditTagsDialog = false
                                }
                            } else {
                                controller.saveTrackTagsToFile(updated)
                                controller.showEditTagsDialog = false
                            }
                        }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Save", color = PowerampOnAccent, fontWeight = FontWeight.ExtraBold.appWeight)
                }
            }
        }
    }
}

@Composable
private fun TagEditInput(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = PowerampTextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold.appWeight)
        Spacer(modifier = Modifier.height(2.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(fontFamily = AppFontFamily, color = PowerampTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold.appWeight),
            cursorBrush = SolidColor(ThemeBerryPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(PowerampPillBg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * 4. Sleep Timer Dialog (`10_sleep_timer.png`).
 */
@Composable
private fun PowerampSleepTimerDialogContent(controller: PowerampController) {
    var draftMinutes by remember { mutableIntStateOf(controller.sleepTimerMinutes) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable { controller.showSleepTimerDialog = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 30.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(PowerampDialogBg)
                .clickable(enabled = false) {}
                .padding(22.dp)
        ) {
            // Header row: Clock icon + "Sleep Timer: disabled"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = "Sleep Timer",
                    tint = ThemeBerryPrimary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (draftMinutes == 0) "Sleep Timer: disabled" else "Sleep Timer: $draftMinutes min.",
                    color = PowerampTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold.appWeight
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Centered Status Text ("Disabled" or "15 minutes")
            Text(
                text = if (draftMinutes == 0) "Disabled" else "$draftMinutes min",
                color = ThemeBerryPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold.appWeight,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Rotary Knob in Center (`10_sleep_timer.png`)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                PowerampRotaryKnob(
                    value = (draftMinutes / 120f).coerceIn(0f, 1f),
                    onValueChange = { norm ->
                        draftMinutes = ((norm * 120f) / 5f).toInt() * 5
                    },
                    size = 118.dp,
                    bipolar = true,
                    showGreenArc = draftMinutes > 0
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Checkbox row: "Play last song to end"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        controller.sleepPlayLastSongToEnd = !controller.sleepPlayLastSongToEnd
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (controller.sleepPlayLastSongToEnd) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = "Play last song to end",
                    tint = if (controller.sleepPlayLastSongToEnd) ThemeBerryPrimary else PowerampTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Play last song to end",
                    color = PowerampTextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Action Buttons: Disable (left), Cancel, OK pill (right) (`10_sleep_timer.png`)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Disable",
                    color = PowerampTextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold.appWeight,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            controller.setSleepTimer(0)
                            controller.showSleepTimerDialog = false
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Cancel",
                        color = PowerampTextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold.appWeight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { controller.showSleepTimerDialog = false }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(ThemeBerryPrimary)
                            .clickable {
                                controller.setSleepTimer(if (draftMinutes == 0) 15 else draftMinutes)
                                controller.showSleepTimerDialog = false
                            }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OK",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold.appWeight
                        )
                    }
                }
            }
        }
    }
}

/**
 * 5. Equalizer Presets Selection Bottom Sheet (`06_eq_presets.png`).
 */
@Composable
private fun PowerampEqPresetBottomSheetContent(controller: PowerampController) {
    var presetQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("All") }
    // The sheet stays open while the picker is shown so the result reaches this launcher; close it afterwards
    val importAutoEq = rememberAutoEqImportLauncher(controller) { controller.showEqPresetSheet = false }

    val filteredPresets = controller.eqPresets.filter { preset ->
        val matchesQuery = presetQuery.isBlank() || preset.name.contains(presetQuery, ignoreCase = true)
        val matchesChip = when (activeFilter) {
            "User" -> preset.category == "User"
            "Built-in" -> preset.category == "Built-in"
            "AutoEq" -> preset.category == "AutoEq"
            "Graphic" -> !preset.isParametric
            "Parametric" -> preset.isParametric
            else -> true
        }
        matchesQuery && matchesChip
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable { controller.showEqPresetSheet = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .height(520.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(PowerampDialogBg)
                .clickable(enabled = false) {}
                .padding(16.dp)
        ) {
            // Search bar inside Preset Picker (`06_eq_presets.png`)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(PowerampPillBg)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search Presets",
                    tint = PowerampTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (presetQuery.isEmpty()) {
                        Text("Search", color = Color(0xFF9EA0A5), fontSize = 15.sp, fontWeight = FontWeight.Bold.appWeight)
                    }
                    BasicTextField(
                        value = presetQuery,
                        onValueChange = { presetQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = AppFontFamily, color = PowerampTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold.appWeight),
                        cursorBrush = SolidColor(ThemeBerryPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear",
                    tint = PowerampTextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { presetQuery = "" }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 1 Filter Chips: User, Built-in, AutoEq | Graphic, Parametric (`06_eq_presets.png`)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("User", "Built-in", "AutoEq", "Graphic", "Parametric")) { chip ->
                    val selected = activeFilter == chip
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (selected) ThemeBerryPrimary else PowerampBorderGray)
                            .clickable { activeFilter = if (selected) "All" else chip }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chip,
                            color = if (selected) PowerampOnAccent else PowerampTextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold.appWeight
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2 Filter Chips: Assigned | Device | x Reset filters (`06_eq_presets.png`)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .border(1.dp, Color(0xFFD2D5DC), RoundedCornerShape(15.dp))
                        .clickable { activeFilter = "User" }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Assigned", color = Color(0xFF4B4F58), fontSize = 12.sp, fontWeight = FontWeight.Bold.appWeight)
                }

                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .border(1.dp, Color(0xFFD2D5DC), RoundedCornerShape(15.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Device", color = Color(0xFF4B4F58), fontSize = 12.sp, fontWeight = FontWeight.Bold.appWeight)
                }

                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .border(1.dp, Color(0xFFD2D5DC), RoundedCornerShape(15.dp))
                        .clickable {
                            activeFilter = "All"
                            presetQuery = ""
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕  Reset filters", color = Color(0xFF4B4F58), fontSize = 12.sp, fontWeight = FontWeight.Bold.appWeight)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Grouped Presets List with Green Frequency Curve Thumbnails (`06_eq_presets.png`)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val grouped = filteredPresets.groupBy { it.category }
                // AutoEq: import a headphone profile (ParametricEQ.txt) instead of shipping fake built-ins
                val showAutoEq = activeFilter in listOf("All", "AutoEq", "Parametric") &&
                    (presetQuery.isBlank() || "autoeq".contains(presetQuery.trim(), ignoreCase = true) ||
                        controller.parametricProfileName.contains(presetQuery.trim(), ignoreCase = true) ||
                        grouped.containsKey("AutoEq"))
                val autoEqItems: LazyListScope.() -> Unit = {
                    if (controller.parametricProfileName.isNotBlank()) {
                        item {
                            AutoEqSheetRow(
                                title = controller.parametricProfileName,
                                subtitle = "Imported profile · ${controller.parametricBands.size} filters",
                                icon = Icons.Filled.Tune,
                                selected = controller.parametricEqEnabled,
                                onClick = {
                                    controller.setParametricEq(true)
                                    controller.showEqPresetSheet = false
                                }
                            )
                        }
                    }
                    item {
                        AutoEqSheetRow(
                            title = "Import AutoEQ profile…",
                            subtitle = "Pick a ParametricEQ.txt for your headphones",
                            icon = Icons.Filled.FileOpen,
                            onClick = importAutoEq
                        )
                    }
                }
                if (showAutoEq && !grouped.containsKey("AutoEq")) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AutoEq",
                                color = PowerampTextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold.appWeight
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            HorizontalDivider(color = PowerampBorderGray, thickness = 1.dp)
                        }
                    }
                    autoEqItems()
                }
                grouped.forEach { (catName, presetsInCat) ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = catName,
                                color = PowerampTextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold.appWeight
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            HorizontalDivider(color = PowerampBorderGray, thickness = 1.dp)
                        }
                    }
                    if (catName == "AutoEq" && showAutoEq) autoEqItems()

                    items(presetsInCat) { preset ->
                        val isSelected = controller.selectedPresetName == preset.name ||
                            (controller.selectedPresetName == "Preset" && preset.name == "Graphic")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    controller.applyEqPreset(preset)
                                    controller.showEqPresetSheet = false
                                }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mini frequency curve thumbnail canvas
                            Canvas(
                                modifier = Modifier
                                    .width(76.dp)
                                    .height(36.dp)
                            ) {
                                val w = size.width
                                val h = size.height
                                val cy = h * 0.45f
                                val path = Path()
                                val bands = preset.bands
                                val step = w / (bands.size - 1).coerceAtLeast(1)
                                bands.forEachIndexed { idx, db ->
                                    val x = idx * step
                                    val y = cy - (db / 15f) * (h * 0.35f)
                                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(
                                    path = path,
                                    color = if (preset.isParametric) PowerampTextSecondary else ThemeBerryPrimary,
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                tint = PowerampTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    color = PowerampTextPrimary,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold.appWeight
                                )
                                if (preset.category == "User") {
                                    Text(
                                        text = "Default Preset",
                                        color = PowerampTextSecondary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold.appWeight
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = ThemeBerryPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            var showAssignMenu by remember(preset.name) { mutableStateOf(false) }
                            Box {
                                Text(
                                    text = "Assign",
                                    color = ThemeBerryPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold.appWeight,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, ThemeBerryPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .clickable { showAssignMenu = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                DropdownMenu(
                                    expanded = showAssignMenu,
                                    onDismissRequest = { showAssignMenu = false }
                                ) {
                                    val (curOut, curDev) = controller.detectCurrentOutput()
                                    DropdownMenuItem(
                                        text = { Text("Assign to ${curOut.label}") },
                                        onClick = {
                                            showAssignMenu = false
                                            controller.assignPresetToCurrentOutput(preset.name)
                                        }
                                    )
                                    if (!curDev.isNullOrBlank() && (curOut == OutputKind.BLUETOOTH || curOut == OutputKind.USB)) {
                                        DropdownMenuItem(
                                            text = { Text("Assign to $curDev") },
                                            onClick = {
                                                showAssignMenu = false
                                                controller.assignPresetToCurrentDevice(preset.name)
                                            }
                                        )
                                    }
                                    val curTrack = controller.currentTrack
                                    if (curTrack != null) {
                                        DropdownMenuItem(
                                            text = { Text("Assign to this song") },
                                            onClick = {
                                                showAssignMenu = false
                                                controller.assignPresetToCurrentTrack(preset.name)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 6. Reverb Preset Picker Dialog.
 */
@Composable
private fun PowerampReverbPresetDialogContent(controller: PowerampController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable { controller.showReverbPresetSheet = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(PowerampDialogBg)
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Text(
                text = "Reverb Presets",
                color = PowerampTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold.appWeight
            )
            Spacer(modifier = Modifier.height(12.dp))
            controller.reverbPresets.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            controller.applyReverbPreset(preset)
                            controller.showReverbPresetSheet = false
                        }
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = preset.name,
                        color = PowerampTextPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold.appWeight
                    )
                    if (controller.selectedReverbPreset == preset.name) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Active",
                            tint = ThemeBerryPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 7. Add To Playlist Dialog & Create New Playlist.
 */
@Composable
private fun PowerampPlaylistDialogContent(controller: PowerampController) {
    val track = controller.actionTargetTrack ?: controller.currentTrack
    var newPlaylistName by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable { controller.showAddToPlaylistDialog = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(PowerampDialogBg)
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Text(
                text = "Add to Playlist",
                color = PowerampTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold.appWeight
            )
            Spacer(modifier = Modifier.height(12.dp))

            controller.playlists.forEach { pl ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (track != null) {
                                controller.addTrackToPlaylist(track, pl.id)
                            }
                            controller.showAddToPlaylistDialog = false
                        }
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(pl.name, color = PowerampTextPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Bold.appWeight)
                    Text("${pl.trackIds.size} songs", color = PowerampTextSecondary, fontSize = 12.5.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            TagEditInput("New Playlist Name", newPlaylistName) { newPlaylistName = it }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { controller.showAddToPlaylistDialog = false }) {
                    Text("Close", color = PowerampTextSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(ThemeBerryPrimary)
                        .clickable {
                            if (newPlaylistName.isNotBlank()) {
                                controller.createPlaylist(newPlaylistName, track)
                                controller.showAddToPlaylistDialog = false
                            }
                        }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ Create", color = PowerampOnAccent, fontWeight = FontWeight.ExtraBold.appWeight)
                }
            }
        }
    }
}

/**
 * 8. Full Poweramp Settings Screen (`17_settings_clean.png`).
 */
@Composable
private fun PowerampSettingsOverlayContent(controller: PowerampController) {
    // Opens straight on a page when asked to (e.g. Visualization menu > Settings)
    var activeSubSetting by remember {
        mutableStateOf<String?>(controller.pendingSettingsSection.also { controller.pendingSettingsSection = null })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PowerampBg)
    ) {
        // Modern Curved Berry Top Bar matching theme
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ThemeBerryHeaderGradient)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (activeSubSetting != null) activeSubSetting = null
                        else controller.showSettingsScreen = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = activeSubSetting ?: "Settings",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold.appWeight,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable { controller.showSettingsScreen = false },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close Settings",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        HorizontalDivider(color = PowerampBorderGray, thickness = 1.dp)

        if (activeSubSetting != null) {
            // Interactive, fully functional sub-settings view
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (activeSubSetting) {
                    "Look and Feel" -> {
                        item {
                            SettingChipSelectorRow(
                                title = "Theme",
                                options = THEME_MODES,
                                selected = controller.skinTheme,
                                onSelect = { mode ->
                                    controller.skinTheme = mode
                                    // Neon green belongs to the Poweramp theme; on light backgrounds it is unreadable
                                    if (mode == "Poweramp") controller.accentColor = POWERAMP_GREEN
                                    else if (controller.accentColor == POWERAMP_GREEN && mode == "Light") controller.accentColor = "Berry"
                                }
                            )
                        }
                        item {
                            AccentColorPickerRow(
                                selected = controller.accentColor,
                                onSelect = { controller.accentColor = it }
                            )
                        }
                        item {
                            SettingChipSelectorRow(
                                title = "Settings Font",
                                subtitle = "Default is lighter, Bold+ heavier; Alternative font uses the system serif family",
                                options = SETTINGS_FONTS,
                                selected = controller.settingsFont,
                                onSelect = {
                                    controller.settingsFont = it
                                    AppTheme.font = it
                                }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Waveseek Smooth Animation",
                                subtitle = "Progress glides every frame; off, it moves in one-second steps",
                                checked = controller.smoothSeekbarAnimation,
                                onCheckedChange = { controller.smoothSeekbarAnimation = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Play/Pause Morphing Animation",
                                subtitle = "Animate the play/pause icon; off, it switches instantly",
                                checked = controller.playPauseMorphAnimation,
                                onCheckedChange = { controller.playPauseMorphAnimation = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Track Slide Transitions",
                                subtitle = "Smooth horizontal slide animation on track change",
                                checked = controller.trackSlideTransitions,
                                onCheckedChange = { controller.trackSlideTransitions = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Status Bar Overlay",
                                subtitle = "Show the system status bar; off, swipe down from the top to see it briefly",
                                checked = controller.showStatusBarOverlay,
                                onCheckedChange = { controller.showStatusBarOverlay = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Rating Pill",
                                subtitle = "Like / dislike buttons below the player controls",
                                checked = controller.showRatingPill,
                                onCheckedChange = { controller.showRatingPill = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Audio Info Line",
                                subtitle = "Sample rate, bitrate and format below the player controls",
                                checked = controller.showMetaLine,
                                onCheckedChange = { controller.showMetaLine = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Filename as Title",
                                subtitle = "Fallback to raw audio file name if tag title is empty",
                                checked = controller.filenameAsTitle,
                                onCheckedChange = { controller.filenameAsTitle = it }
                            )
                        }
                    }

                    "Audio" -> {
                        item {
                            SettingChipSelectorRow(
                                title = "EQ Bands (ISO)",
                                options = listOf("5", "10", "15", "31"),
                                selected = controller.graphicBandCount.toString(),
                                onSelect = { opt ->
                                    opt.toIntOrNull()?.let { controller.setGraphicBandConfig(it, 31f, 16000f) }
                                }
                            )
                        }
                        item {
                            SettingSliderRow(
                                title = "Custom EQ Band Count",
                                displayValue = "${controller.graphicBandCount} bands (${controller.graphicStartHz.toInt()} Hz – ${controller.graphicEndHz.toInt()} Hz)",
                                fraction = ((controller.graphicBandCount - 5) / 27f).coerceIn(0f, 1f),
                                onFractionChange = { frac ->
                                    val count = (5 + (frac * 27f).toInt()).coerceIn(5, 32)
                                    if (count != controller.graphicBandCount) {
                                        controller.setGraphicBandConfig(count)
                                    }
                                }
                            )
                        }
                        item {
                            // Log scale 20..500 Hz
                            val lo = kotlin.math.ln(20f); val hi = kotlin.math.ln(500f)
                            SettingSliderRow(
                                title = "Custom EQ Start Frequency",
                                displayValue = "${controller.graphicStartHz.toInt()} Hz",
                                fraction = ((kotlin.math.ln(controller.graphicStartHz) - lo) / (hi - lo)).coerceIn(0f, 1f),
                                onFractionChange = { frac ->
                                    val hz = kotlin.math.exp(lo + frac * (hi - lo))
                                    if (kotlin.math.abs(hz - controller.graphicStartHz) >= 1f) {
                                        controller.setGraphicBandConfig(controller.graphicBandCount, hz, controller.graphicEndHz)
                                    }
                                }
                            )
                        }
                        item {
                            // Log scale 2..20 kHz
                            val lo = kotlin.math.ln(2000f); val hi = kotlin.math.ln(20000f)
                            SettingSliderRow(
                                title = "Custom EQ End Frequency",
                                displayValue = "${controller.graphicEndHz.toInt()} Hz",
                                fraction = ((kotlin.math.ln(controller.graphicEndHz) - lo) / (hi - lo)).coerceIn(0f, 1f),
                                onFractionChange = { frac ->
                                    val hz = kotlin.math.exp(lo + frac * (hi - lo))
                                    if (kotlin.math.abs(hz - controller.graphicEndHz) >= 10f) {
                                        controller.setGraphicBandConfig(controller.graphicBandCount, controller.graphicStartHz, hz)
                                    }
                                }
                            )
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PowerampDialogBg)
                                    .clickable { controller.showSleepTimerDialog = true }
                                    .padding(14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Sleep Timer", color = PowerampTextPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold.appWeight)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(if (controller.sleepTimerMinutes > 0) "${controller.sleepTimerMinutes} min remaining (active)" else "Tap to set auto-stop timer", color = PowerampTextSecondary, fontSize = 12.5.sp)
                                    }
                                    Icon(imageVector = Icons.Filled.Schedule, contentDescription = "Sleep Timer", tint = ThemeBerryPrimary, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                        item {
                            SettingSliderRow(
                                title = "Crossfade Duration",
                                displayValue = "%.1f seconds".format(controller.crossfadeDurationSec),
                                fraction = controller.crossfadeDurationSec / 10f,
                                onFractionChange = { controller.crossfadeDurationSec = it * 10f }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Auto Crossfade",
                                subtitle = "Smooth crossfade on track change and completion",
                                checked = controller.crossfadeEnabled,
                                onCheckedChange = { controller.crossfadeEnabled = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Gapless Playback",
                                subtitle = "Zero latency transitions between album tracks",
                                checked = controller.gaplessEnabled,
                                onCheckedChange = { controller.gaplessEnabled = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "MusicAmp audio engine",
                                subtitle = "Decodes and plays audio inside the app, which enables the DSP (EQ, tone, reverb, stereo), ReplayGain, DVC, hi-res output and DSD playback. Off: the Android MediaPlayer with the device equalizer only.",
                                checked = controller.useNativeEngine,
                                onCheckedChange = { controller.setNativeEngine(it) }
                            )
                        }
                        item {
                            SettingChipSelectorRow(
                                title = "ReplayGain Mode",
                                subtitle = "Evens out loudness using the ReplayGain tags in your files: per track, or per album to keep the album's own dynamics. Needs the MusicAmp engine.",
                                options = listOf("Off", "Track Gain", "Album Gain"),
                                selected = controller.replayGainMode,
                                onSelect = { controller.replayGainMode = it }
                            )
                        }
                        item {
                            SettingSliderRow(
                                title = "ReplayGain Preamp",
                                subtitle = "Extra gain added on top of the ReplayGain adjustment",
                                displayValue = "%+.1f dB".format(controller.replayGainPreampDb),
                                fraction = (controller.replayGainPreampDb + 6f) / 12f,
                                onFractionChange = { controller.replayGainPreampDb = (it * 12f) - 6f }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Direct Volume Control (DVC)",
                                subtitle = "Applies volume inside the app while the system media volume is held at maximum. Gives the EQ headroom to boost without clipping. Needs the MusicAmp engine." +
                                    if (controller.dvcActive) " Active now." else "",
                                checked = controller.dvcEnabled,
                                onCheckedChange = { controller.dvcEnabled = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Bit-Perfect Output (USB DAC)",
                                subtitle = if (controller.bitPerfectEnabled) controller.bitPerfectStatus
                                    else "Sends untouched audio to a USB DAC at the track's native rate. Android 14+. EQ, effects, crossfade and tempo are bypassed while active.",
                                checked = controller.bitPerfectEnabled,
                                onCheckedChange = { controller.setBitPerfect(it) }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Hi-Res Audio Output",
                                subtitle = "32-bit float output at the track's native sample rate, where the device supports it. Needs the MusicAmp engine.",
                                checked = controller.hiResAudioEnabled,
                                onCheckedChange = { controller.hiResAudioEnabled = it }
                            )
                        }
                        item {
                            SettingChipSelectorRow(
                                title = "Audio Buffer Size",
                                subtitle = "Size of the MusicAmp engine's output buffer. Larger buffers resist dropouts under load but react more slowly to EQ and volume changes.",
                                options = listOf("Small (25ms)", "Normal (50ms)", "Large (100ms)", "Huge (200ms)"),
                                selected = controller.audioBufferSize,
                                onSelect = { controller.audioBufferSize = it }
                            )
                        }
                        item {
                            // Audio Processing Info Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PowerampBorderGray)
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "AUDIO ENGINE STATUS",
                                        color = ThemeBerryPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold.appWeight,
                                        letterSpacing = 0.8.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val onOff = { b: Boolean -> if (b) "on" else "off" }
                                    val eqLine = when {
                                        controller.bitPerfectActive -> "bypassed (bit-perfect)"
                                        controller.parametricEqEnabled -> "parametric, ${controller.parametricBands.count { it.enabled }} bands (${onOff(controller.eqEnabled)})"
                                        else -> "10-band graphic (${onOff(controller.eqEnabled)})"
                                    }
                                    Text(
                                        text = "Engine: ${controller.engineStatus}\n" +
                                            "Output: ${if (controller.bitPerfectActive) "Bit-perfect, ${controller.bitPerfectStatus.removePrefix("Active on ")}" else if (controller.nativeEngineActive) "MusicAmp engine" else "Android MediaPlayer"}\n" +
                                            "Current track: ${controller.currentTrack?.bottomBadgeInfo ?: "none"}\n" +
                                            "Equalizer: $eqLine\n" +
                                            "DVC: ${if (controller.dvcActive) "active" else "off"}   ReplayGain: ${controller.replayGainMode}\n" +
                                            "Tone: ${onOff(controller.toneEnabled)}   Limiter: ${onOff(controller.limitEnabled)}\n" +
                                            "Crossfade: ${if (controller.crossfadeEnabled) "%.1f s".format(controller.crossfadeDurationSec) else "off"}   Gapless: ${onOff(controller.gaplessEnabled)}",
                                        color = PowerampTextPrimary,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }

                    "Visualization" -> {
                        item {
                            SettingChipSelectorRow(
                                title = "Active Visualization Preset",
                                options = controller.visPresets.map { it.name },
                                selected = controller.currentVisPreset.name,
                                onSelect = { name ->
                                    val idx = controller.visPresets.indexOfFirst { it.name == name }
                                    if (idx >= 0) controller.currentVisPresetIndex = idx
                                }
                            )
                        }
                        item {
                            SettingSliderRow(
                                title = "Faded Controls Opacity",
                                displayValue = "${(controller.visFadedControlsOpacity * 100).toInt()}%",
                                fraction = controller.visFadedControlsOpacity,
                                onFractionChange = { controller.visFadedControlsOpacity = it.coerceIn(0.10f, 1f) }
                            )
                        }
                        item {
                            SettingChipSelectorRow(
                                title = "Preset Auto-Switch Duration",
                                options = listOf("5s", "10s", "15s", "30s", "Manual"),
                                selected = when (controller.visPresetDurationSec) {
                                    5 -> "5s"
                                    10 -> "10s"
                                    15 -> "15s"
                                    30 -> "30s"
                                    else -> "Manual"
                                },
                                onSelect = { sel ->
                                    controller.visPresetDurationSec = when (sel) {
                                        "5s" -> 5
                                        "10s" -> 10
                                        "15s" -> 15
                                        "30s" -> 30
                                        else -> 0
                                    }
                                }
                            )
                        }
                        item {
                            SettingChipSelectorRow(
                                title = "Rendering Frame Rate Target",
                                options = listOf("30 FPS", "60 FPS (Ultra Smooth)"),
                                selected = if (controller.visFpsTarget == 60) "60 FPS (Ultra Smooth)" else "30 FPS",
                                onSelect = { controller.visFpsTarget = if (it.startsWith("60")) 60 else 30 }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Show Preset Notification Toast",
                                subtitle = "Popup banner when preset changes automatically",
                                checked = controller.visShowPresetToast,
                                onCheckedChange = { controller.visShowPresetToast = it }
                            )
                        }
                    }

                    "Background" -> {
                        item {
                            SettingChipSelectorRow(
                                title = "Background Appearance",
                                options = listOf("Pure Black (OLED)", "Album Art Blur", "Blurred Mountain Glow"),
                                selected = controller.backgroundType,
                                onSelect = { controller.backgroundType = it }
                            )
                        }
                        item {
                            SettingSliderRow(
                                title = "Blur Intensity",
                                displayValue = "${(controller.backgroundBlurRadius * 100).toInt()}%",
                                fraction = controller.backgroundBlurRadius,
                                onFractionChange = { controller.backgroundBlurRadius = it }
                            )
                        }
                        item {
                            SettingSliderRow(
                                title = "Background Saturation",
                                displayValue = "${(controller.backgroundSaturation * 100).toInt()}%",
                                fraction = controller.backgroundSaturation / 2f,
                                onFractionChange = { controller.backgroundSaturation = it * 2f }
                            )
                        }
                        item {
                            SettingSliderRow(
                                title = "Dim Behind Controls",
                                displayValue = "${(controller.backgroundDimOpacity * 100).toInt()}%",
                                fraction = controller.backgroundDimOpacity / 0.8f,
                                onFractionChange = { controller.backgroundDimOpacity = it * 0.8f }
                            )
                        }
                    }

                    "Album Art" -> {
                        item {
                            SettingToggleRow(
                                title = "Download High-Res Artwork",
                                subtitle = "Fetch high resolution 1000x1000 cover art",
                                checked = controller.highResAlbumArt,
                                onCheckedChange = { controller.highResAlbumArt = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Prefer Embedded ID3 Art",
                                subtitle = "Prioritize artwork embedded inside audio files",
                                checked = controller.preferEmbeddedArt,
                                onCheckedChange = { controller.preferEmbeddedArt = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Download on Wi-Fi only",
                                subtitle = "Skip cover art downloads on metered mobile networks",
                                checked = controller.albumArtWifiOnly,
                                onCheckedChange = { controller.albumArtWifiOnly = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Album Art on Lock Screen",
                                subtitle = "Show cover on lockscreen with media controls",
                                checked = controller.albumArtOnLockscreen,
                                onCheckedChange = { controller.albumArtOnLockscreen = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Full-screen Lockscreen Player",
                                subtitle = "Shows a full-screen player over the lock screen when you lock the phone with MusicAmp open. " +
                                    "Android doesn't let apps open it from the background, so locking from another app shows the normal lock screen.",
                                checked = controller.lockscreenPlayerEnabled,
                                onCheckedChange = { controller.lockscreenPlayerEnabled = it }
                            )
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PowerampDialogBg)
                                    .clickable {
                                        controller.showToast("Cache Cleared", "Album art temporary files removed")
                                    }
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Clear Album Art Cache", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold.appWeight, fontSize = 15.sp)
                            }
                        }
                    }

                    "Library" -> {
                        item {
                            Text("MUSIC FOLDERS", color = ThemeBerryPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold.appWeight)
                        }
                        items(listOf("/sdcard/Music", "/sdcard/Download", "/sdcard/Podcasts")) { path ->
                            val isChecked = controller.selectedMusicFolders.contains(path)
                            SettingToggleRow(
                                title = path,
                                subtitle = if (isChecked) "Folder included in library scan" else "Excluded",
                                checked = isChecked,
                                onCheckedChange = {
                                    if (isChecked) controller.selectedMusicFolders.remove(path)
                                    else controller.selectedMusicFolders.add(path)
                                }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Auto-Scan on Startup",
                                subtitle = "Re-index files added or changed since the last start",
                                checked = controller.autoScanOnStartup,
                                onCheckedChange = { controller.autoScanOnStartup = it }
                            )
                        }
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PowerampDialogBg)
                                    .padding(14.dp)
                            ) {
                                Text("Tag Separators", color = PowerampTextPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold.appWeight)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Split multi-value Artist, Genre and Composer tags for library grouping without changing track display text",
                                    color = PowerampTextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val allSeps = listOf(";" to ";", "/" to "/", " feat. " to "feat.", ", " to ", ")
                                    items(allSeps) { (sep, label) ->
                                        val active = sep in controller.tagSeparators
                                        Box(
                                            modifier = Modifier
                                                .height(30.dp)
                                                .clip(RoundedCornerShape(15.dp))
                                                .background(if (active) ThemeBerryPrimary else PowerampPillBg)
                                                .border(1.dp, if (active) ThemeBerryPrimary else PowerampBorderGray, RoundedCornerShape(15.dp))
                                                .clickable { controller.toggleTagSeparator(sep) }
                                                .padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (active) PowerampOnAccent else PowerampTextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold.appWeight
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Text("EXTRA FOLDERS (DSD, .CUE, .LRC)", color = ThemeBerryPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold.appWeight)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Android does not list DSD files (.dsf/.dff) as music and hides .cue and .lrc files from music apps. " +
                                    "Grant a folder once and MusicAmp reads them from there.",
                                color = PowerampTextSecondary, fontSize = 12.5.sp
                            )
                        }
                        items(controller.extraFolders.toList()) { uri ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PowerampBorderGray)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    controller.extraFolderLabel(uri), color = PowerampTextPrimary, fontSize = 14.5.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "Remove", color = ThemeBerryPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold.appWeight,
                                    modifier = Modifier.clickable { controller.removeExtraFolder(uri) }.padding(6.dp)
                                )
                            }
                        }
                        item {
                            val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                                androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
                            ) { picked -> if (picked != null) controller.addExtraFolder(picked) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PowerampBorderGray)
                                    .clickable { folderPicker.launch(null) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+ Add folder", color = PowerampTextPrimary, fontWeight = FontWeight.Bold.appWeight, fontSize = 15.sp)
                            }
                        }
                        item {
                            SettingToggleRow(
                                title = "Ignore Short Tracks (< 30s)",
                                subtitle = "Filter out voice notes, ringtones and alarms",
                                checked = controller.ignoreShortTracks,
                                onCheckedChange = { controller.ignoreShortTracks = it }
                            )
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(ThemeBerryPrimary)
                                    .clickable {
                                        controller.refreshLibrary(showFeedback = true)
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Full Rescan Media Library Now", color = PowerampOnAccent, fontWeight = FontWeight.Bold.appWeight, fontSize = 16.sp)
                            }
                        }
                    }

                    "Headset/Bluetooth" -> {
                        item {
                            SettingToggleRow(
                                title = "Resume on Headset Connect",
                                subtitle = "Auto-resume playback when headphones plugged in",
                                checked = controller.resumeOnHeadsetConnect,
                                onCheckedChange = { controller.resumeOnHeadsetConnect = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Pause on Headset Disconnect",
                                subtitle = "Safely pause playback when unplugged",
                                checked = controller.pauseOnHeadsetDisconnect,
                                onCheckedChange = { controller.pauseOnHeadsetDisconnect = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Headset Buttons Control",
                                subtitle = "Single click Play/Pause, double click Next Track",
                                checked = controller.headsetButtonsEnabled,
                                onCheckedChange = { controller.headsetButtonsEnabled = it }
                            )
                        }
                        item {
                            SettingToggleRow(
                                title = "Bluetooth Direct Volume Control",
                                subtitle = "Uses Direct Volume Control on Bluetooth outputs too: volume is applied inside the app while the Bluetooth media volume is held at maximum. Off: Bluetooth keeps normal system volume. Needs the MusicAmp engine.",
                                checked = controller.bluetoothDvcEnabled,
                                onCheckedChange = { controller.bluetoothDvcEnabled = it }
                            )
                        }
                        item {
                            Text(
                                text = "EQ PRESET ASSIGNMENTS",
                                color = PowerampTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold.appWeight,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            val hasAny = controller.presetForOutput.isNotEmpty() ||
                                controller.presetForDevice.isNotEmpty() ||
                                controller.presetForTrack.isNotEmpty()
                            if (!hasAny) {
                                Text(
                                    text = "No EQ presets assigned yet. Use Assign in the EQ Presets sheet.",
                                    color = PowerampTextSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }
                        items(controller.presetForOutput.entries.toList()) { (kind, presetName) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(kind.label, color = PowerampTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold.appWeight)
                                    Text("Output → $presetName", color = PowerampTextSecondary, fontSize = 12.sp)
                                }
                                Text(
                                    text = "Remove",
                                    color = ThemeBerryPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold.appWeight,
                                    modifier = Modifier.clickable { controller.presetForOutput.remove(kind) }
                                )
                            }
                        }
                        items(controller.presetForDevice.entries.toList()) { (devName, presetName) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(devName, color = PowerampTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold.appWeight)
                                    Text("Device → $presetName", color = PowerampTextSecondary, fontSize = 12.sp)
                                }
                                Text(
                                    text = "Remove",
                                    color = ThemeBerryPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold.appWeight,
                                    modifier = Modifier.clickable { controller.presetForDevice.remove(devName) }
                                )
                            }
                        }
                        items(controller.presetForTrack.entries.toList()) { (trackId, presetName) ->
                            val trackTitle = controller.allTracks.firstOrNull { it.id == trackId }?.title ?: "Track #$trackId"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(trackTitle, color = PowerampTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold.appWeight)
                                    Text("Song → $presetName", color = PowerampTextSecondary, fontSize = 12.sp)
                                }
                                Text(
                                    text = "Remove",
                                    color = ThemeBerryPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold.appWeight,
                                    modifier = Modifier.clickable { controller.presetForTrack.remove(trackId) }
                                )
                            }
                        }
                    }

                    "Misc" -> {
                        item {
                            SettingToggleRow(
                                title = "Android Auto Support",
                                subtitle = "Display optimized automotive player interface",
                                checked = controller.androidAutoEnabled,
                                onCheckedChange = { controller.androidAutoEnabled = it }
                            )
                        }
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PowerampDialogBg)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    "Notification & Android Auto Buttons (${controller.notificationActions.size}/6)",
                                    color = PowerampTextPrimary,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.SemiBold.appWeight
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Choose up to 6 actions for the playback notification and Android Auto. Compact view keeps Previous / Play / Next.",
                                    color = PowerampTextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(MediaButtonAction.entries) { action ->
                                        val active = action in controller.notificationActions
                                        Box(
                                            modifier = Modifier
                                                .height(30.dp)
                                                .clip(RoundedCornerShape(15.dp))
                                                .background(if (active) ThemeBerryPrimary else PowerampPillBg)
                                                .border(1.dp, if (active) ThemeBerryPrimary else PowerampBorderGray, RoundedCornerShape(15.dp))
                                                .clickable { controller.toggleNotificationAction(action) }
                                                .padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = action.label,
                                                color = if (active) PowerampOnAccent else PowerampTextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold.appWeight
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            SettingToggleRow(
                                title = "Scrobble with Last.fm",
                                subtitle = "Broadcast tracks to Simple Last.fm Scrobbler",
                                checked = controller.scrobblingEnabled,
                                onCheckedChange = { controller.scrobblingEnabled = it }
                            )
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PowerampDialogBg)
                                    .clickable {
                                        controller.showToast("Settings Exported", "Backup saved to internal storage")
                                    }
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Export Settings & Presets Backup", color = PowerampTextPrimary, fontWeight = FontWeight.Bold.appWeight, fontSize = 15.sp)
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFFFEBEE))
                                    .clickable {
                                        controller.resetSettingsToDefault()
                                    }
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Reset All Settings to Factory Default", color = Color(0xFFC62828), fontWeight = FontWeight.Bold.appWeight, fontSize = 15.sp)
                            }
                        }
                    }

                    "About" -> {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(PowerampDialogBg)
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "MUSICAMP",
                                    color = PowerampTextPrimary,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black.appWeight,
                                    fontStyle = FontStyle.Italic,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "build-1013-uni (Full Version)",
                                    color = ThemeBerryPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold.appWeight
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("✓ Lifetime License Activated", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold.appWeight, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Audiophile 64-bit float DSP audio engine with custom 10-band graphic/parametric equalizer, dynamic Waveseek navigation, real-time visualization, and Direct Volume Control (DVC).",
                                    color = Color(0xFF555860),
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PowerampDialogBg)
                                    .clickable { controller.showChangelogDialog = true }
                                    .padding(16.dp)
                            ) {
                                Text("View Engine Changelog", color = PowerampTextPrimary, fontWeight = FontWeight.Bold.appWeight, fontSize = 15.sp)
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PowerampDialogBg)
                                    .clickable { controller.showHelpDialog = true }
                                    .padding(16.dp)
                            ) {
                                Text("Help & FAQ", color = PowerampTextPrimary, fontWeight = FontWeight.Bold.appWeight, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    Text(
                        text = "Settings",
                        color = ThemeBerryPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold.appWeight,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                val itemsList = listOf(
                    Triple("Look and Feel", "Skin, player interface, language, notifications", Color(0xFF8380B6) to Icons.Filled.Layers),
                    Triple("Audio", "Crossfade, replay gain, volume, output", Color(0xFFC36A84) to Icons.Filled.VolumeUp),
                    Triple("Visualization", "Faded controls opacity, preset duration", Color(0xFF9167C2) to Icons.Filled.GraphicEq),
                    Triple("Background", "Blur, details, intensity, saturation", Color(0xFF5D8F8D) to Icons.Filled.Wallpaper),
                    Triple("Album Art", "Download, quality, cache cleanup", Color(0xFF6B9964) to Icons.Filled.Image),
                    Triple("Library", "Rescan, music folders, list, queue options", Color(0xFF5A739C) to Icons.Filled.Folder),
                    Triple("Headset/Bluetooth", "Resume on connection, buttons, DVC per output", Color(0xFF4F87A8) to Icons.Filled.Tune),
                    Triple("Misc", "Tweaks, export/import settings, battery optimization", PowerampTextSecondary to Icons.Filled.Settings),
                    Triple("About", "MusicAmp build-1013-uni (Full Version)", Color(0xFF5C6B73) to Icons.Filled.Info)
                )

                items(itemsList) { (title, summary, colorAndIcon) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeSubSetting = title }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = colorAndIcon.second,
                            contentDescription = title,
                            tint = colorAndIcon.first,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = PowerampTextPrimary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Normal.appWeight
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = summary,
                                color = Color(0xFF52545A),
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PowerampDialogBg)
            .clickable { onCheckedChange(!checked) }
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = PowerampTextPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold.appWeight)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = PowerampTextSecondary, fontSize = 12.5.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSliderRow(
    title: String,
    displayValue: String,
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PowerampDialogBg)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = PowerampTextPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold.appWeight)
            Text(displayValue, color = ThemeBerryPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold.appWeight)
        }
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = PowerampTextSecondary, fontSize = 12.5.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = onFractionChange,
            colors = SliderDefaults.colors(
                thumbColor = ThemeBerryPrimary,
                activeTrackColor = ThemeBerryPrimary,
                inactiveTrackColor = PowerampBorderGray
            )
        )
    }
}

@Composable
private fun SettingChipSelectorRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PowerampDialogBg)
            .padding(14.dp)
    ) {
        Text(title, color = PowerampTextPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold.appWeight)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = PowerampTextSecondary, fontSize = 12.5.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(options) { opt ->
                val isSelected = opt == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) ThemeBerryPrimary else PowerampPillBg)
                        .clickable { onSelect(opt) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = opt,
                        color = if (isSelected) PowerampOnAccent else PowerampTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold.appWeight else FontWeight.Medium.appWeight
                    )
                }
            }
        }
    }
}

/**
 * 9. Poweramp Toast Banner (`14_vis_panel.png`).
 */
@Composable
private fun PowerampToastOverlayContent(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 22.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xEE2B2C30))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold.appWeight
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = PowerampBorderGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }
        }
    }
}

/**
 * 10. Simple Info Modal for Changelog, Help, and Album Art Selector.
 */
@Composable
private fun PowerampSimpleInfoDialogContent(
    title: String,
    body: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(PowerampDialogBg)
                .clickable(enabled = false) {}
                .padding(22.dp)
        ) {
            Text(title, color = PowerampTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold.appWeight)
            Spacer(modifier = Modifier.height(12.dp))
            Text(body, color = Color(0xFF33363D), fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(ThemeBerryPrimary)
                    .clickable { onDismiss() }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("OK", color = PowerampOnAccent, fontWeight = FontWeight.ExtraBold.appWeight)
            }
        }
    }
}

/** Row of colour swatches for choosing the accent colour; the choice recolours the whole app instantly. */
@Composable
private fun AccentColorPickerRow(selected: String, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PowerampDialogBg)
            .padding(14.dp)
    ) {
        Text("Accent colour", color = PowerampTextPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold.appWeight)
        Spacer(modifier = Modifier.height(4.dp))
        Text(selected, color = PowerampTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val entries = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val dyn = (if (AppTheme.isDark) AppTheme.dynamicDarkPrimary else AppTheme.dynamicLightPrimary)
                        ?: Color(0xFF6750A4)
                    add(MATERIAL_YOU to Triple(dyn, dyn, dyn))
                }
                addAll(ACCENT_COLORS.entries.map { it.key to it.value })
            }
            items(entries) { (name, shades) ->
                val isSelected = name == selected
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(shades.first, shades.third)))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) PowerampTextPrimary else PowerampBorderGray,
                            shape = CircleShape
                        )
                        .clickable { onSelect(name) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = name, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ---- Public entry points: each overlay picks up the Look and Feel settings font ----
@Composable
fun PowerampMainMenuOverlay(controller: PowerampController) =
    AppTextStyleScope { PowerampMainMenuOverlayContent(controller) }

@Composable
fun PowerampTrackMenuDialog(controller: PowerampController) =
    AppTextStyleScope { PowerampTrackMenuDialogContent(controller) }

@Composable
fun PowerampInfoTagsSheet(controller: PowerampController) =
    AppTextStyleScope { PowerampInfoTagsSheetContent(controller) }

@Composable
fun PowerampEditTagsDialog(controller: PowerampController) =
    AppTextStyleScope { PowerampEditTagsDialogContent(controller) }

@Composable
fun PowerampSleepTimerDialog(controller: PowerampController) =
    AppTextStyleScope { PowerampSleepTimerDialogContent(controller) }

@Composable
fun PowerampEqPresetBottomSheet(controller: PowerampController) =
    AppTextStyleScope { PowerampEqPresetBottomSheetContent(controller) }

@Composable
fun PowerampReverbPresetDialog(controller: PowerampController) =
    AppTextStyleScope { PowerampReverbPresetDialogContent(controller) }

@Composable
fun PowerampPlaylistDialog(controller: PowerampController) =
    AppTextStyleScope { PowerampPlaylistDialogContent(controller) }

@Composable
fun PowerampSettingsOverlay(controller: PowerampController) =
    LookAndFeelScope(controller) { PowerampSettingsOverlayContent(controller) }

@Composable
fun PowerampToastOverlay(title: String, subtitle: String?, modifier: Modifier = Modifier) =
    AppTextStyleScope { PowerampToastOverlayContent(title, subtitle, modifier) }

/**
 * Volume panel shown at the top while the volume keys drive the app's own (DVC) volume.
 * A dark glass card with the level as a large number, the gain in dB and a glowing bar
 * whose fill glides to each new step.
 */
@Composable
private fun PowerampVolumeOverlayContent(percent: Int, modifier: Modifier = Modifier) {
    val level = percent.coerceIn(0, 100)
    val fill by animateFloatAsState(
        targetValue = level / 100f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "volumeFill"
    )
    // DVC gain is (level/100)^2.5, so in dB that is 50·log10(level/100)
    val dbText = if (level == 0) "Muted" else "%.1f dB".format(50f * kotlin.math.log10(level / 100f))
    Column(
        modifier = modifier
            .padding(horizontal = 28.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF2A2B31), Color(0xFF1B1C20)))
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(start = 16.dp, end = 18.dp, top = 12.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ThemeBerryPrimary.copy(alpha = if (level == 0) 0.08f else 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        level == 0 -> Icons.Filled.VolumeOff
                        level < 50 -> Icons.Filled.VolumeDown
                        else -> Icons.Filled.VolumeUp
                    },
                    contentDescription = "Volume",
                    tint = if (level == 0) PowerampTextSecondary else ThemeBerryPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Volume",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
                Text(
                    text = dbText,
                    color = PowerampTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium.appWeight
                )
            }
            Text(
                text = "$level",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold.appWeight
            )
            Text(
                text = "%",
                color = PowerampTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold.appWeight,
                modifier = Modifier.padding(start = 2.dp, top = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Track, fill and a glowing thumb at the current level
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val thumb = 14.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(ThemeBerryPrimary.copy(alpha = 0.55f), ThemeBerryPrimary)
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - thumb) * fill)
                    .size(thumb)
                    .shadow(6.dp, CircleShape, ambientColor = ThemeBerryPrimary, spotColor = ThemeBerryPrimary)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun PowerampVolumeOverlay(percent: Int, modifier: Modifier = Modifier) =
    AppTextStyleScope { PowerampVolumeOverlayContent(percent, modifier) }

@Composable
fun PowerampSimpleInfoDialog(title: String, body: String, onDismiss: () -> Unit) =
    AppTextStyleScope { PowerampSimpleInfoDialogContent(title, body, onDismiss) }

/**
 * 11. Add Internet Radio Stream dialog (title + http(s) URL).
 */
@Composable
fun PowerampAddStreamDialog(controller: PowerampController) = AppTextStyleScope {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val urlValid = url.trim().let { it.startsWith("http://", true) || it.startsWith("https://", true) } && url.trim().length > 8

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable { controller.showAddStreamDialog = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(PowerampDialogBg)
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Text(
                text = "Add Stream",
                color = PowerampTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold.appWeight
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Internet radio or any direct http(s) audio stream",
                color = PowerampTextSecondary,
                fontSize = 12.5.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            TagEditInput("Title", title) { title = it }
            TagEditInput("Stream URL", url) { url = it.trim() }
            if (url.isNotBlank() && !urlValid) {
                Text(
                    text = "Use an http:// or https:// address",
                    color = Color(0xFFE85050),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { controller.showAddStreamDialog = false }) {
                    Text("Cancel", color = PowerampTextSecondary, fontWeight = FontWeight.Bold.appWeight)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(if (urlValid) ThemeBerryPrimary else PowerampBorderGray)
                        .clickable(enabled = urlValid) {
                            controller.addStream(title, url)
                            controller.showAddStreamDialog = false
                        }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Add",
                        color = if (urlValid) PowerampOnAccent else PowerampTextSecondary,
                        fontWeight = FontWeight.ExtraBold.appWeight
                    )
                }
            }
        }
    }
}
