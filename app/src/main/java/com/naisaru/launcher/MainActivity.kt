package com.naisaru.launcher

import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.PI

private val GlassText = Color(0xFFFAFFF9)
private val GroupIcons = listOf("apps","phone","chat","people","play","game","tools","photo","star","heart","folder")
private data class InstalledApp(val label:String, val pkg:String, val activity:String) {
    val key get() = pkg + "/" + activity
}
private data class AppGroup(val id:String, val name:String, val symbol:String)
private data class HomeObject(
    val id:Long, val kind:String, val label:String,
    val pkg:String="", val activity:String="", val uri:String="",
    val x:Float=90f, val y:Float=180f,
    val scale:Float=1f, val rotation:Float=0f,
    val action:String="none", val widgetId:Int=-1
)

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("naisaru", MODE_PRIVATE) }
    private val legacyPrefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }
    private val widgets by lazy { AppWidgetHost(this, 48092) }
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var installed by mutableStateOf(emptyList<InstalledApp>())
    private var objects by mutableStateOf(emptyList<HomeObject>())
    private var categories by mutableStateOf(defaultGroups())
    private var membership by mutableStateOf(emptyMap<String,String>())
    private var orderedKeys by mutableStateOf(emptyList<String>())
    private var drawerBlur by mutableFloatStateOf(22f)
    private var glassOpacity by mutableFloatStateOf(.72f)
    private var animPreset by mutableStateOf("Classic")
    private var animMillis by mutableFloatStateOf(360f)
    private var springAmount by mutableFloatStateOf(.45f)
    private var gridEnabled by mutableStateOf(false)
    private var gridStepDp by mutableIntStateOf(32)

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        readSettings()
        refreshApps()
        setContent { MaterialTheme(colorScheme = NaisaruColors) { LauncherScreen() } }
    }
    override fun onStart() { super.onStart(); try { widgets.startListening() } catch (_:Exception) {} }
    override fun onStop() { try { widgets.stopListening() } catch (_:Exception) {}; super.onStop() }
    override fun onPause() { saveSettings(); super.onPause() }
    private fun defaultGroups() = listOf(
        AppGroup("all","Все","apps"),
        AppGroup("communication","Связь","phone"),
        AppGroup("social","Соцсети","people"),
        AppGroup("media","Медиа","play"),
        AppGroup("games","Игры","game"),
        AppGroup("utilities","Инструменты","tools")
    )
    private fun refreshApps() {
        @Suppress("DEPRECATION")
        val activities=packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
        installed=activities.map {
            InstalledApp(it.loadLabel(packageManager).toString(),it.activityInfo.packageName,it.activityInfo.name)
        }.distinctBy { it.key }.sortedBy { it.label.lowercase() }
        if(orderedKeys.isEmpty()) orderedKeys=installed.map { it.key }
    }
    private fun openApp(app:InstalledApp) {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(app.pkg,app.activity))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_:Exception) { refreshApps() }
    }
    private fun openObject(obj:HomeObject) {
        if(obj.kind=="app") openApp(InstalledApp(obj.label,obj.pkg,obj.activity))
        if(obj.kind!="image") return
        when {
            obj.action.startsWith("url:") -> {
                val uri=Uri.parse(obj.action.removePrefix("url:"))
                if(uri.scheme.equals("http",true) || uri.scheme.equals("https",true)) {
                    try { startActivity(Intent(Intent.ACTION_VIEW,uri)) } catch (_:Exception) { }
                }
            }
            obj.action.startsWith("app:") -> installed.firstOrNull {
                it.key==obj.action.removePrefix("app:")
            }?.let(::openApp)
            obj.action!="none" -> installed.firstOrNull {it.pkg==obj.action}?.let(::openApp)
        }
    }
    private fun addApp(app:InstalledApp) {
        objects=objects+HomeObject(System.currentTimeMillis(),"app",app.label,app.pkg,app.activity)
        saveSettings()
    }
    private fun readSettings() {
        try {
            val old=legacyPrefs.getString("home",null)
            val arr=JSONArray(prefs.getString("objects",old ?: "[]"))
            objects=(0 until arr.length()).map { i ->
                val v=arr.getJSONObject(i)
                HomeObject(
                    v.optLong("id",System.currentTimeMillis()+i),v.optString("kind","app"),v.optString("label",""),
                    v.optString("pkg",v.optString("package","")),
                    v.optString("activity",v.optString("class","")),
                    v.optString("uri",""),v.optDouble("x",90.0).toFloat(),v.optDouble("y",180.0).toFloat(),
                    v.optDouble("scale",1.0).toFloat(),v.optDouble("rotation",0.0).toFloat(),
                    v.optString("action","none"),v.optInt("widgetId",-1)
                )
            }
            val groupArray=JSONArray(prefs.getString("categories","[]"))
            categories=(0 until groupArray.length()).mapNotNull { i ->
                groupArray.optJSONObject(i)?.let { AppGroup(it.optString("id"),it.optString("name"),it.optString("symbol","folder")) }
            }.ifEmpty { defaultGroups() }
            val map=JSONObject(prefs.getString("membership",legacyPrefs.getString("assigned","{}")))
            membership=map.keys().asSequence().associateWith { map.optString(it) }
            val order=JSONArray(prefs.getString("order","[]"))
            orderedKeys=(0 until order.length()).map { order.optString(it) }
            drawerBlur=prefs.getFloat("blur",22f)
            glassOpacity=prefs.getFloat("opacity",.72f)
            animPreset=prefs.getString("animation","Classic")?.let {if(it=="Liquid") "Classic" else it} ?: "Classic"
            animMillis=prefs.getFloat("duration",360f)
            springAmount=prefs.getFloat("spring",.45f)
            gridEnabled=prefs.getBoolean("grid_enabled",false)
            gridStepDp=prefs.getInt("grid_spacing",32).coerceIn(16,64)
        } catch (_:Exception) { objects=emptyList();categories=defaultGroups() }
    }
    private fun saveSettings() {
        val tiles=JSONArray()
        objects.forEach { obj ->
            tiles.put(JSONObject().apply {
                put("id",obj.id);put("kind",obj.kind);put("label",obj.label)
                put("pkg",obj.pkg);put("activity",obj.activity);put("uri",obj.uri)
                put("x",obj.x.toDouble());put("y",obj.y.toDouble())
                put("scale",obj.scale.toDouble());put("rotation",obj.rotation.toDouble())
                put("action",obj.action);put("widgetId",obj.widgetId)
            })
        }
        val groupArray=JSONArray()
        categories.forEach { g ->
            groupArray.put(JSONObject().apply {put("id",g.id);put("name",g.name);put("symbol",g.symbol)})
        }
        prefs.edit().putString("objects",tiles.toString())
            .putString("categories",groupArray.toString())
            .putString("membership",JSONObject(membership).toString())
            .putString("order",JSONArray(orderedKeys).toString())
            .putFloat("blur",drawerBlur).putFloat("opacity",glassOpacity)
            .putString("animation",animPreset).putFloat("duration",animMillis)
            .putFloat("spring",springAmount)
            .putBoolean("grid_enabled",gridEnabled).putInt("grid_spacing",gridStepDp).apply()
    }
    private fun reorder(app:InstalledApp, delta:Int, currentGroup:String, query:String) {
        val list=filteredApps(currentGroup,query)
        val old=list.indexOfFirst { it.key==app.key }
        val other=old+delta
        if(old<0 || other !in list.indices) return
        val keys=orderedKeys.toMutableList()
        val a=keys.indexOf(list[old].key)
        val b=keys.indexOf(list[other].key)
        if(a>=0 && b>=0) {
            val tmp=keys[a];keys[a]=keys[b];keys[b]=tmp
            orderedKeys=keys;saveSettings()
        }
    }
    private fun filteredApps(group:String,query:String):List<InstalledApp> {
        return installed.filter {
            it.label.contains(query,true) &&
                (group=="all" || membership[it.pkg]==group)
        }.sortedWith(compareBy<InstalledApp> {
            val i=orderedKeys.indexOf(it.key); if (i<0) Int.MAX_VALUE else i
        }.thenBy { it.label.lowercase() })
    }
    private fun moveToGroup(app:InstalledApp,target:String) {
        membership=if(target=="all") membership-app.pkg else membership+(app.pkg to target)
        saveSettings()
    }
    private fun placeBefore(app:InstalledApp,target:InstalledApp) {
        if(app.key==target.key) return
        val keys=(orderedKeys+installed.map {it.key}).distinct().toMutableList()
        keys.remove(app.key)
        val i=keys.indexOf(target.key)
        keys.add(if(i<0) keys.size else i,app.key)
        orderedKeys=keys
        saveSettings()
    }
    private fun snapCoordinate(value:Float,cellPx:Float):Float =
        if(cellPx>0f) (value/cellPx).roundToInt()*cellPx else value

    private fun deleteObject(obj:HomeObject) {
        if(obj.kind=="widget" && obj.widgetId>=0) try { widgets.deleteAppWidgetId(obj.widgetId) } catch (_:Exception) {}
        objects=objects.filterNot { it.id==obj.id };saveSettings()
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun LauncherScreen() {
        var drawer by remember { mutableStateOf(false) }
        var homeMenu by remember { mutableStateOf(false) }
        var edit by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<Long?>(null) }
        var groupId by remember { mutableStateOf("all") }
        var search by remember { mutableStateOf("") }
        var showSearch by remember {mutableStateOf(false)}
        var draggingApp by remember {mutableStateOf<InstalledApp?>(null)}
        var dragDelta by remember {mutableStateOf(Offset.Zero)}
        var dragOrigin by remember {mutableStateOf(Offset.Zero)}
        val groupRects=remember {mutableStateMapOf<String,Rect>()}
        val appRects=remember {mutableStateMapOf<String,Rect>()}
        var groupEditor by remember { mutableStateOf(false) }
        var appMenu by remember { mutableStateOf<InstalledApp?>(null) }
        var settings by remember { mutableStateOf(false) }
        var themeMenu by remember { mutableStateOf(false) }
        var stickerAction by remember { mutableStateOf(false) }
        var actionSearch by remember {mutableStateOf("")}
        var actionUrl by remember {mutableStateOf("")}
        var urlInvalid by remember {mutableStateOf(false)}
        var gridSizesOpen by remember {mutableStateOf(false)}
        val rawDragPositions=remember { mutableStateMapOf<Long,Offset>() }
        var pendingWidgetId by remember { mutableIntStateOf(-1) }

        val configuration=LocalConfiguration.current
        val density=LocalDensity.current
        val screenWidth=with(density){configuration.screenWidthDp.dp.toPx()}
        val selectedObject=objects.firstOrNull { it.id==selected }

        val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri:Uri? ->
            if(uri!=null) {
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_:Exception) {}
                objects=objects+HomeObject(System.currentTimeMillis(),"image","Изображение",uri=uri.toString(),x=screenWidth*.23f,y=190f)
                edit=true;homeMenu=false;saveSettings()
            }
        }
        val wallpaperPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri:Uri? ->
            if(uri!=null) {
                try { contentResolver.openInputStream(uri)?.use { WallpaperManager.getInstance(this).setStream(it) } }
                catch (_:Exception) {}
            }
        }
        val widgetPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val allocated=pendingWidgetId
            val id=result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,allocated) ?: allocated
            if(result.resultCode==RESULT_OK && id>=0 && widgetManager.getAppWidgetInfo(id)!=null) {
                objects=objects+HomeObject(System.currentTimeMillis(),"widget","Виджет",x=screenWidth*.16f,y=180f,scale=1.8f,widgetId=id)
                edit=true;homeMenu=false;saveSettings()
            } else if(allocated>=0) try { widgets.deleteAppWidgetId(allocated) } catch (_:Exception) {}
            pendingWidgetId=-1
        }
        fun chooseWidget() {
            try {
                val id=widgets.allocateAppWidgetId()
                pendingWidgetId=id
                widgetPicker.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
                })
            } catch (_:Exception) {
                if(pendingWidgetId>=0) try { widgets.deleteAppWidgetId(pendingWidgetId) } catch (_:Exception) {}
                pendingWidgetId=-1
            }
        }
        BackHandler(drawer || homeMenu || edit || settings || groupEditor || themeMenu || appMenu!=null || stickerAction) {
            when {
                stickerAction -> stickerAction=false
                appMenu!=null -> appMenu=null
                groupEditor -> groupEditor=false
                settings -> settings=false
                themeMenu -> themeMenu=false
                drawer -> drawer=false
                homeMenu -> homeMenu=false
                edit -> {edit=false;selected=null;saveSettings()}
            }
        }
        DisposableEffect(drawer,drawerBlur) {
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attrs=window.attributes
                attrs.blurBehindRadius=if(drawer) drawerBlur.roundToInt() else 0
                window.attributes=attrs
            }
            onDispose {}
        }

        Box(Modifier.fillMaxSize().background(Color.Transparent)
            .pointerInput(drawer,edit,homeMenu) {
                if(!edit && !homeMenu) awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                    var delta=Offset.Zero
                    var triggered=false
                    do {
                        val event=awaitPointerEvent(pass=PointerEventPass.Initial)
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        if(!change.pressed) break
                        delta+=change.positionChange()
                        val threshold=40.dp.toPx()
                        if(!triggered && kotlin.math.abs(delta.y)>threshold &&
                            kotlin.math.abs(delta.y)>kotlin.math.abs(delta.x)*1.3f) {
                            if(delta.y<0f && !drawer) { drawer=true; triggered=true }
                            else if(delta.y>0f && drawer) {drawer=false;triggered=true}
                        }
                    } while(!triggered)
                }
            }
            .pointerInput(drawer,edit) {
                if(!drawer) detectTapGestures(
                    onLongPress={ if(!edit) {homeMenu=true;selected=null} },
                    onTap={ if(!edit) homeMenu=false }
                )
            }) {
            if(edit && gridEnabled && !drawer) {
                Canvas(Modifier.fillMaxSize()) {
                    val cell=gridStepDp.dp.toPx()
                    if(cell>1f) {
                        val stroke=1.dp.toPx()
                        val tint=Color(0xFF91D8A0).copy(alpha=.20f)
                        var horizontal=0f
                        while(horizontal<size.height) {
                            drawLine(tint,Offset(0f,horizontal),
                                Offset(size.width,horizontal),strokeWidth=stroke)
                            horizontal+=cell
                        }
                        var vertical=0f
                        while(vertical<size.width) {
                            drawLine(tint,Offset(vertical,0f),
                                Offset(vertical,size.height),strokeWidth=stroke)
                            vertical+=cell
                        }
                    }
                }
            }
            objects.forEach { obj ->
                key(obj.id) {
                    val isSelected=edit && obj.id==selected
                    val baseSize=if(obj.kind=="widget") 144f else 82f
                    var objectModifier:Modifier=Modifier
                        .offset {IntOffset(obj.x.roundToInt(),obj.y.roundToInt())}
                        .size((baseSize*obj.scale).dp)
                        .rotate(obj.rotation)
                    if(isSelected) objectModifier=objectModifier.border(1.5.dp,GlassText, RoundedCornerShape(7.dp))
                    if(edit) {
                        objectModifier=objectModifier.pointerInput(obj.id,gridEnabled,gridStepDp) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                selected=obj.id
                                val current=objects.firstOrNull {it.id==obj.id}
                                    ?: return@detectTransformGestures
                                val raw=rawDragPositions[obj.id]
                                    ?: Offset(current.x,current.y)
                                val moved=raw+pan
                                rawDragPositions[obj.id]=moved
                                val cell=with(density){gridStepDp.dp.toPx()}
                                val x=if(gridEnabled) snapCoordinate(moved.x,cell) else moved.x
                                val y=if(gridEnabled) snapCoordinate(moved.y,cell) else moved.y
                                objects=objects.map { item ->
                                    if(item.id==obj.id) item.copy(
                                        x=x.coerceIn(-screenWidth*.5f,screenWidth*1.3f),
                                        y=y.coerceAtLeast(-100f),
                                        scale=(item.scale*zoom).coerceIn(.25f,3.7f),
                                        rotation=(item.rotation+rotation)%360f
                                    ) else item
                                }
                            }
                        }.clickable {selected=obj.id}
                    } else if(obj.kind=="app" || (obj.kind=="image" && obj.action!="none")) {
                        objectModifier=objectModifier.clickable {openObject(obj)}
                    }
                    Box(objectModifier,contentAlignment=Alignment.Center) {
                        when(obj.kind) {
                            "app" -> AppIcon(obj.pkg,Modifier.fillMaxSize())
                            "image" -> {
                                val bitmap=remember(obj.uri) {
                                    try {
                                        contentResolver.openInputStream(Uri.parse(obj.uri))?.use {input ->
                                            val options=BitmapFactory.Options().apply {inSampleSize=2}
                                            BitmapFactory.decodeStream(input,null,options)?.asImageBitmap()
                                        }
                                    } catch (_:Exception) {null}
                                }
                                if(bitmap!=null) Image(bitmap,contentDescription=obj.label,modifier=Modifier.fillMaxSize())
                                else Icon(Icons.Default.BrokenImage,obj.label,tint=GlassText)
                            }
                            "widget" -> {
                                val info=remember(obj.widgetId) {widgetManager.getAppWidgetInfo(obj.widgetId)}
                                if(info!=null) AndroidView(
                                    factory={ctx -> widgets.createView(ctx,obj.widgetId,info)},
                                    modifier=Modifier.fillMaxSize()
                                ) else Icon(Icons.Default.Widgets,"Виджет",tint=GlassText)
                            }
                        }
                        if(isSelected) {
                            listOf(Alignment.TopStart,Alignment.TopEnd,Alignment.BottomStart).forEach { corner ->
                                Box(Modifier.align(corner).size(11.dp).clip(CircleShape)
                                    .background(GlassText))
                            }
                        }
                    }
                }
            }

            // Separate 40 dp rotation grip with global pointer coordinates.
            // Using positionInRoot keeps the angle correct as the grip itself moves.
            if(edit && selectedObject!=null && !drawer) {
                val item=selectedObject
                val extent=with(density){
                    ((if(item.kind=="widget") 144f else 82f)*item.scale).dp.toPx()
                }
                val radius=extent/2f
                val centerX=item.x+radius
                val centerY=item.y+radius
                val radians=Math.toRadians(item.rotation.toDouble())
                val handleX=centerX+(cos(radians)-sin(radians)).toFloat()*radius
                val handleY=centerY+(sin(radians)+cos(radians)).toFloat()*radius
                val handleRadius=with(density){20.dp.toPx()}
                var gripTopLeft by remember(item.id) {mutableStateOf(Offset.Zero)}
                var pivot by remember(item.id) {mutableStateOf(Offset.Zero)}
                var beginningAngle by remember(item.id) {mutableFloatStateOf(0f)}
                var beginningRotation by remember(item.id) {mutableFloatStateOf(0f)}
                Box(
                    Modifier
                        .offset {IntOffset((handleX-handleRadius).roundToInt(),
                            (handleY-handleRadius).roundToInt())}
                        .size(40.dp).zIndex(8f)
                        .onGloballyPositioned {gripTopLeft=it.positionInRoot()}
                        .clip(CircleShape)
                        .background(Color(0xFF79CC86))
                        .border(2.dp,Color.White,CircleShape)
                        .pointerInput(item.id) {
                            detectDragGestures(
                                onDragStart={localStart ->
                                    val current=objects.firstOrNull {it.id==item.id} ?: item
                                    val r=with(density){
                                        ((if(current.kind=="widget") 144f else 82f)*current.scale).dp.toPx()/2f
                                    }
                                    pivot=Offset(current.x+r,current.y+r)
                                    beginningRotation=current.rotation
                                    val rootStart=gripTopLeft+localStart
                                    beginningAngle=atan2(rootStart.y-pivot.y,rootStart.x-pivot.x)
                                },
                                onDragEnd={saveSettings()},
                                onDragCancel={saveSettings()}
                            ) { change,_ ->
                                change.consume()
                                val rootPoint=gripTopLeft+change.position
                                val currentAngle=atan2(rootPoint.y-pivot.y,rootPoint.x-pivot.x)
                                val degrees=(currentAngle-beginningAngle)*180f/PI.toFloat()
                                val value=((beginningRotation+degrees)%360f+360f)%360f
                                objects=objects.map { obj ->
                                    if(obj.id==item.id) obj.copy(rotation=value) else obj
                                }
                            }
                        },
                    contentAlignment=Alignment.Center
                ) {
                    Icon(Icons.Default.RotateRight,"Вращать объект",
                        tint=Color(0xFF0F2514),modifier=Modifier.size(25.dp))
                }
            }

            AnimatedVisibility(
                visible=homeMenu && !drawer,
                enter=slideInVertically(animationSpec=tween(animMillis.toInt().coerceAtLeast(100))){ it / 2 }+fadeIn(),
                exit=slideOutVertically(animationSpec=tween(180)){it/2}+fadeOut(),
                modifier=Modifier.align(Alignment.BottomCenter).padding(start=12.dp,end=12.dp,bottom=30.dp)
            ) {
                LiquidPanel(Modifier.fillMaxWidth(),radius=28.dp,opacity=glassOpacity) {
                    Row(Modifier.fillMaxWidth().padding(vertical=14.dp,horizontal=4.dp),
                        horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                        MenuChoice("widgets","Виджеты",Modifier.weight(1f)){homeMenu=false;chooseWidget()}
                        MenuChoice("edit","Редактировать",Modifier.weight(1.25f)){homeMenu=false;edit=true}
                        MenuChoice("wallpaper","Обои",Modifier.weight(.8f)){homeMenu=false;wallpaperPicker.launch("image/*")}
                        MenuChoice("palette","Темы",Modifier.weight(.75f)){homeMenu=false;themeMenu=true}
                        MenuChoice("settings","Настройки",Modifier.weight(1f)){homeMenu=false;settings=true}
                    }
                }
            }
            AnimatedVisibility(
                visible=edit && !drawer && !homeMenu,
                modifier=Modifier.align(Alignment.BottomCenter)
                    .padding(start=12.dp,end=12.dp,bottom=12.dp),
                enter=slideInVertically(animationSpec=tween(260)){it}+fadeIn(),
                exit=slideOutVertically(animationSpec=tween(180)){it}+fadeOut()
            ) {
                LiquidPanel(Modifier.fillMaxWidth(),opacity=glassOpacity) {
                    Column(Modifier.padding(horizontal=12.dp,vertical=10.dp),
                        verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.SpaceBetween,
                            verticalAlignment=Alignment.CenterVertically) {
                            SmallChoice(Icons.Default.AddPhotoAlternate,"Изображение") {
                                imagePicker.launch(arrayOf("image/png","image/webp","image/*"))
                            }
                            SmallChoice(Icons.Default.Widgets,"Виджет") {chooseWidget()}
                            SmallChoice(Icons.Default.Apps,"Приложение") {drawer=true}
                            SmallChoice(Icons.Default.CheckCircle,"Готово") {
                                edit=false
                                selected=null
                                rawDragPositions.clear()
                                saveSettings()
                            }
                        }
                        HorizontalDivider(color=Color(0x55458A59))
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Default.GridOn,contentDescription=null,tint=GreenAccent,
                                modifier=Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Привязка к сетке",color=GlassText,
                                fontSize=13.sp,modifier=Modifier.weight(1f))
                            Switch(
                                checked=gridEnabled,
                                onCheckedChange={ value ->
                                    gridEnabled=value
                                    rawDragPositions.clear()
                                    saveSettings()
                                },
                                modifier=Modifier.height(32.dp),
                                colors=SwitchDefaults.colors(
                                    checkedThumbColor=Color(0xFF0C2010),
                                    checkedTrackColor=GreenAccent,
                                    uncheckedTrackColor=Color(0xFF303D33)
                                )
                            )
                            Spacer(Modifier.width(5.dp))
                            Box {
                                TextButton(onClick={gridSizesOpen=true}) {
                                    Text("${gridStepDp} dp",color=GlassText,fontSize=12.sp)
                                }
                                DropdownMenu(expanded=gridSizesOpen,onDismissRequest={gridSizesOpen=false}) {
                                    listOf(16,24,32,48,64).forEach { value ->
                                        DropdownMenuItem(
                                            text={Text("${value} dp")},
                                            onClick={
                                                gridStepDp=value
                                                gridSizesOpen=false
                                                rawDragPositions.clear()
                                                saveSettings()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if(selectedObject!=null) {
                            HorizontalDivider(color=Color(0x55458A59))
                            Row(Modifier.fillMaxWidth(),
                                verticalAlignment=Alignment.CenterVertically) {
                                Text(selectedObject.label,
                                    color=GlassText,fontWeight=FontWeight.SemiBold,
                                    fontSize=13.sp,modifier=Modifier.weight(1f),
                                    maxLines=1,overflow=TextOverflow.Ellipsis)
                                if(selectedObject.kind=="image") {
                                    IconButton(onClick={
                                        actionSearch=""
                                        actionUrl=if(selectedObject.action.startsWith("url:"))
                                            selectedObject.action.removePrefix("url:") else ""
                                        urlInvalid=false
                                        stickerAction=true
                                    },modifier=Modifier.size(38.dp)) {
                                        Icon(Icons.Default.TouchApp,"Действие изображения",
                                            tint=GreenAccent,modifier=Modifier.size(21.dp))
                                    }
                                }
                                IconButton(onClick={
                                    val id=selectedObject.id
                                    objects=objects.map{if(it.id==id)it.copy(rotation=0f) else it}
                                    saveSettings()
                                },modifier=Modifier.size(38.dp)) {
                                    Icon(Icons.Default.RotateLeft,"Сбросить поворот",
                                        tint=GlassText,modifier=Modifier.size(21.dp))
                                }
                                IconButton(onClick={
                                    val id=selectedObject.id
                                    deleteObject(selectedObject)
                                    rawDragPositions.remove(id)
                                    selected=null
                                },modifier=Modifier.size(38.dp)) {
                                    Icon(Icons.Default.DeleteOutline,"Удалить объект",
                                        tint=Color(0xFFFFB6A8),modifier=Modifier.size(21.dp))
                                }
                            }
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text("Размер",color=GlassText,fontSize=12.sp)
                                Slider(
                                    value=selectedObject.scale,
                                    onValueChange={newSize ->
                                        val id=selectedObject.id
                                        objects=objects.map{
                                            if(it.id==id)it.copy(scale=newSize) else it
                                        }
                                    },
                                    valueRange=.25f..3.7f,
                                    onValueChangeFinished={saveSettings()},
                                    modifier=Modifier.weight(1f)
                                )
                                Text("${(selectedObject.scale*100).roundToInt()}%",
                                    color=GlassText,fontSize=12.sp)
                            }
                            Text("Поворот: ${selectedObject.rotation.roundToInt()}°",
                                color=GlassText.copy(alpha=.75f),fontSize=12.sp)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible=drawer,
                enter=slideInVertically(animationSpec=tween(animMillis.toInt().coerceIn(150,900))) {it}+fadeIn(tween(220)),
                exit=slideOutVertically(animationSpec=tween(240)){it}+fadeOut(tween(160))
            ) {
                Box(Modifier.fillMaxSize().padding(top=12.dp)
                    .background(Brush.verticalGradient(listOf(
                        Color(0x44000000),Color(0xBB07130C),Color(0xEB122119)
                    )))) {
                    Column(Modifier.fillMaxSize().padding(start=18.dp,end=18.dp,top=26.dp,bottom=18.dp)) {
                        Spacer(Modifier.weight(.58f))
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text(categories.firstOrNull {it.id==groupId}?.name ?: "Все",
                                color=GlassText,fontSize=29.sp,fontWeight=FontWeight.Bold,
                                modifier=Modifier.weight(1f))
                            IconButton(onClick={showSearch=!showSearch;if(!showSearch)search=""}) {
                                Icon(Icons.Default.Search,"Поиск",tint=GlassText)
                            }
                            IconButton(onClick={groupEditor=true}) {
                                Icon(Icons.Default.MoreVert,"Редактировать группы",tint=GlassText)
                            }
                        }
                        if(showSearch) OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),
                            singleLine=true,
                            placeholder={Text("Поиск приложений",color=GlassText.copy(alpha=.8f))},
                            colors=OutlinedTextFieldDefaults.colors(
                                focusedTextColor=GlassText,unfocusedTextColor=GlassText,
                                focusedBorderColor=GlassText,unfocusedBorderColor=GlassText.copy(alpha=.5f)
                            ))
                        Spacer(Modifier.height(12.dp))
                        AnimatedContent(targetState=groupId,
                            transitionSpec={
                                (slideInHorizontally(animationSpec=spring(
                                    dampingRatio=Spring.DampingRatioMediumBouncy,
                                    stiffness=Spring.StiffnessMediumLow
                                )) { it/5 }+fadeIn(tween(180)))
                                    .togetherWith(slideOutHorizontally(tween(160)){-it/5}+fadeOut(tween(160)))
                            },label="spring-group-switch",modifier=Modifier.weight(1.4f)
                        ) { currentGroup ->
                            val shown=filteredApps(currentGroup,search)
                            LazyVerticalGrid(
                                GridCells.Fixed(4),modifier=Modifier.fillMaxSize(),
                                verticalArrangement=Arrangement.spacedBy(19.dp),
                                horizontalArrangement=Arrangement.spacedBy(5.dp),
                                contentPadding=PaddingValues(top=6.dp,bottom=14.dp)
                            ) {
                                items(shown,key={it.key}) { app ->
                                    val dragging=draggingApp?.key==app.key
                                    Column(
                                        Modifier.fillMaxWidth()
                                            .alpha(if(dragging) .25f else 1f)
                                            .onGloballyPositioned {
                                                if(!dragging) appRects[app.key]=it.boundsInRoot()
                                            }
                                            .pointerInput(app.key,currentGroup) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart={
                                                        draggingApp=app
                                                        dragDelta=Offset.Zero
                                                        dragOrigin=appRects[app.key]?.center ?: Offset.Zero
                                                    },
                                                    onDragEnd={
                                                        val moving=draggingApp
                                                        val drop=dragOrigin+dragDelta
                                                        if(moving!=null && dragOrigin!=Offset.Zero) {
                                                            val group=groupRects.entries.firstOrNull {
                                                                it.value.contains(drop)
                                                            }?.key
                                                            if(group!=null) {
                                                                moveToGroup(moving,group)
                                                                groupId=group
                                                            } else {
                                                                val otherKey=appRects.entries.firstOrNull {
                                                                    it.key!=moving.key && it.value.contains(drop)
                                                                }?.key
                                                                if(otherKey!=null) installed.firstOrNull{it.key==otherKey}
                                                                    ?.let {placeBefore(moving,it)}
                                                            }
                                                        }
                                                        draggingApp=null
                                                        dragDelta=Offset.Zero
                                                    },
                                                    onDragCancel={draggingApp=null;dragDelta=Offset.Zero}
                                                ) { change,amount ->
                                                    change.consume()
                                                    dragDelta+=amount
                                                }
                                            }
                                            .clickable { if(draggingApp==null)openApp(app) },
                                        horizontalAlignment=Alignment.CenterHorizontally
                                    ) {
                                        AppIcon(app.pkg,Modifier.size(58.dp))
                                        Spacer(Modifier.height(6.dp))
                                        Text(app.label,color=GlassText,fontSize=11.sp,maxLines=2,
                                            overflow=TextOverflow.Ellipsis,lineHeight=13.sp,
                                            textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
                            .background(Color(0x552B4D3B))
                            .border(1.dp,GlassText.copy(alpha=.3f),RoundedCornerShape(25.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical=6.dp,horizontal=3.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            categories.forEach { g ->
                                val active=groupId==g.id
                                val springScale by animateFloatAsState(
                                    targetValue=if(active)1.13f else 1f,
                                    animationSpec=spring(
                                        dampingRatio=Spring.DampingRatioMediumBouncy,
                                        stiffness=Spring.StiffnessLow
                                    ),label="spring-category-icon"
                                )
                                Column(
                                    Modifier.width(59.dp).height(65.dp)
                                        .onGloballyPositioned { groupRects[g.id]=it.boundsInRoot() }
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(if(active) Color.White.copy(alpha=.16f) else Color.Transparent)
                                        .clickable {groupId=g.id},
                                    horizontalAlignment=Alignment.CenterHorizontally,
                                    verticalArrangement=Arrangement.Center
                                ) {
                                    Icon(glyph(g.symbol),g.name,tint=GlassText,
                                        modifier=Modifier.size(25.dp).scale(springScale))
                                    Spacer(Modifier.height(3.dp))
                                    Text(g.name,color=GlassText,fontSize=10.sp,maxLines=1,
                                        textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                    Box(Modifier.width(18.dp).height(3.dp)
                                        .clip(CircleShape)
                                        .background(if(active) GlassText else Color.Transparent))
                                }
                            }
                            IconButton(onClick={groupEditor=true}) {
                                Icon(Icons.Default.AddCircle,"Группы",tint=GlassText)
                            }
                            IconButton(onClick={settings=true}) {
                                Icon(Icons.Default.Settings,"Настройки",tint=GlassText)
                            }
                        }
                    }
                }
            }
            if(drawer && draggingApp!=null && dragOrigin!=Offset.Zero) {
                Box(Modifier.offset {
                    IntOffset((dragOrigin.x+dragDelta.x-29.dp.toPx()).roundToInt(),
                        (dragOrigin.y+dragDelta.y-29.dp.toPx()).roundToInt())
                }.zIndex(12f).size(58.dp),
                    contentAlignment=Alignment.Center) {
                    AppIcon(draggingApp!!.pkg,Modifier.fillMaxSize())
                }
            }
        }

        if(groupEditor) GroupEditor(groupId,{groupEditor=false},{newId->groupId=newId})
        if(appMenu!=null) {
            val app=appMenu!!
            AlertDialog(
                onDismissRequest={appMenu=null},
                title={Text(app.label)},
                text={
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TextButton(onClick={addApp(app);appMenu=null;drawer=false;edit=true}) {
                            Icon(Icons.Default.Add,"",Modifier.size(18.dp));Text("На главный экран")
                        }
                        Text("Группа",fontWeight=FontWeight.SemiBold)
                        categories.forEach {g ->
                            TextButton(onClick={
                                membership=if(g.id=="all") membership-app.pkg else membership+(app.pkg to g.id)
                                saveSettings();appMenu=null
                            }) {
                                Icon(glyph(g.symbol),null,Modifier.size(19.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(g.name)
                                if(membership[app.pkg]==g.id) Icon(Icons.Default.Check,null)
                            }
                        }
                        HorizontalDivider()
                        TextButton(onClick={reorder(app,-1,groupId,search);appMenu=null}) {
                            Icon(Icons.Default.ArrowUpward,null);Text("Выше в списке")
                        }
                        TextButton(onClick={reorder(app,1,groupId,search);appMenu=null}) {
                            Icon(Icons.Default.ArrowDownward,null);Text("Ниже в списке")
                        }
                    }
                },
                confirmButton={TextButton(onClick={appMenu=null}){Text("Закрыть")}}
            )
        }
        if(settings) {
            AlertDialog(
                onDismissRequest={settings=false},
                title={Text("Настройки Naisaru")},
                text={
                    Column(Modifier.heightIn(max=470.dp).verticalScroll(rememberScrollState())) {
                        Text("Размытие фона: "+drawerBlur.roundToInt(),fontWeight=FontWeight.Medium)
                        Slider(drawerBlur,{drawerBlur=it;saveSettings()},valueRange=0f..80f)
                        Text("Непрозрачность панелей: "+(glassOpacity*100).roundToInt()+"%")
                        Slider(glassOpacity,{glassOpacity=it;saveSettings()},valueRange=.25f..1f)
                        Spacer(Modifier.height(8.dp))
                        Text("Набор анимаций",fontWeight=FontWeight.Medium)
                        listOf("Classic","Spring","Smooth","Dynamic").forEach { p ->
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                RadioButton(selected=animPreset==p,onClick={animPreset=p;saveSettings()})
                                Text(p,Modifier.clickable{animPreset=p;saveSettings()})
                            }
                        }
                        Text("Длительность: "+animMillis.roundToInt()+" мс")
                        Slider(animMillis,{animMillis=it;saveSettings()},valueRange=150f..900f)
                        Text("Пружинистость: "+(springAmount*100).roundToInt()+"%")
                        Slider(springAmount,{springAmount=it;saveSettings()},valueRange=0f..1f)
                        TextButton(onClick={settings=false;groupEditor=true}){Text("Редактор групп")}
                    }
                },
                confirmButton={TextButton(onClick={settings=false}){Text("Готово")}}
            )
        }
        if(themeMenu) {
            AlertDialog(
                onDismissRequest={themeMenu=false},
                title={Text("Темы")},
                text={Text("Оформление Naisaru Launcher: чёрные панели и зелёные акценты. Обои можно сменить отдельно, а непрозрачность панелей изменить в настройках.")},
                confirmButton={TextButton(onClick={themeMenu=false;settings=true}){Text("Настройки")}},
                dismissButton={TextButton(onClick={themeMenu=false}){Text("Закрыть")}}
            )
        }
        if(stickerAction && selectedObject!=null) {
            val targetId=selectedObject.id
            AlertDialog(
                onDismissRequest={stickerAction=false},
                title={Text("Действие изображения")},
                text={
                    Column(Modifier.heightIn(max=430.dp)
                        .verticalScroll(rememberScrollState())) {
                        TextButton(onClick={
                            objects=objects.map{
                                if(it.id==targetId) it.copy(action="none") else it
                            }
                            saveSettings()
                            stickerAction=false
                        }) {
                            Icon(Icons.Default.TouchApp,null,modifier=Modifier.size(20.dp))
                            Spacer(Modifier.width(9.dp))
                            Text("Без действия")
                        }
                        Text("Открыть ссылку",fontWeight=FontWeight.SemiBold)
                        OutlinedTextField(
                            value=actionUrl,
                            onValueChange={actionUrl=it;urlInvalid=false},
                            label={Text("Адрес сайта")},
                            placeholder={Text("https://example.com")},
                            singleLine=true,
                            isError=urlInvalid,
                            modifier=Modifier.fillMaxWidth()
                        )
                        if(urlInvalid) Text("Введите корректную ссылку",
                            color=MaterialTheme.colorScheme.error,fontSize=12.sp)
                        TextButton(onClick={
                            val input=actionUrl.trim()
                            val address=if(input.startsWith("http://",true) ||
                                input.startsWith("https://",true)) input else "https://"+input
                            val parsed=Uri.parse(address)
                            if(parsed.host.isNullOrBlank() || !parsed.host!!.contains(".")) {
                                urlInvalid=true
                            } else {
                                objects=objects.map{
                                    if(it.id==targetId) it.copy(action="url:"+address) else it
                                }
                                saveSettings()
                                stickerAction=false
                            }
                        }) {
                            Icon(Icons.Default.Link,null,modifier=Modifier.size(19.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Сохранить ссылку")
                        }
                        HorizontalDivider()
                        Text("Запускать приложение",fontWeight=FontWeight.SemiBold)
                        OutlinedTextField(
                            value=actionSearch,
                            onValueChange={actionSearch=it},
                            label={Text("Поиск приложения")},
                            leadingIcon={Icon(Icons.Default.Search,null)},
                            singleLine=true,modifier=Modifier.fillMaxWidth()
                        )
                        installed.filter {it.label.contains(actionSearch,true)}
                            .take(50).forEach { app ->
                                TextButton(onClick={
                                    objects=objects.map{
                                        if(it.id==targetId) it.copy(action="app:"+app.key)
                                        else it
                                    }
                                    saveSettings()
                                    stickerAction=false
                                },modifier=Modifier.fillMaxWidth()) {
                                    AppIcon(app.pkg,Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(app.label,modifier=Modifier.weight(1f))
                                }
                            }
                    }
                },
                confirmButton={TextButton(onClick={stickerAction=false}){Text("Закрыть")}}
            )
        }
    }

    @Composable
    private fun MenuChoice(icon:String,label:String,modifier:Modifier=Modifier,onClick:()->Unit) {
        Column(modifier.clip(RoundedCornerShape(15.dp)).clickable(onClick=onClick)
            .padding(vertical=6.dp,horizontal=1.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Icon(glyph(icon),label,tint=GlassText,modifier=Modifier.size(25.dp))
            Text(label,color=GlassText,fontSize=10.sp,maxLines=1)
        }
    }
    @Composable
    private fun SmallChoice(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,action:()->Unit) {
        Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick=action)
            .padding(horizontal=5.dp,vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(icon,label,tint=GlassText,modifier=Modifier.size(23.dp))
            Text(label,color=GlassText,fontSize=11.sp)
        }
    }
    @Composable
    private fun AppIcon(pkg:String,modifier:Modifier=Modifier) {
        val bitmap=remember(pkg) {
            try {packageManager.getApplicationIcon(pkg).toBitmap(144,144).asImageBitmap()}
            catch (_:Exception) {null}
        }
        if(bitmap!=null) Image(bitmap,contentDescription=pkg,modifier=modifier)
        else Icon(Icons.Default.Android,pkg,modifier,tint=GlassText)
    }
    @Composable
    private fun GroupEditor(current:String,onDismiss:()->Unit,onSelect:(String)->Unit) {
        var selectedId by remember {mutableStateOf(current)}
        var name by remember {mutableStateOf(categories.firstOrNull{it.id==current}?.name ?: "")}
        var symbol by remember {mutableStateOf(categories.firstOrNull{it.id==current}?.symbol ?: "folder")}
        fun selectGroup(group:AppGroup) {selectedId=group.id;name=group.name;symbol=group.symbol}
        AlertDialog(
            onDismissRequest=onDismiss,
            title={Text("Группы приложений")},
            text={
                Column(Modifier.heightIn(max=470.dp).verticalScroll(rememberScrollState())) {
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        categories.forEach { g ->
                            FilterChip(selected=selectedId==g.id,onClick={selectGroup(g)},
                                label={Text(g.name)},leadingIcon={Icon(glyph(g.symbol),null,Modifier.size(16.dp))})
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(name,onValueChange={ value ->
                        name=value
                        if(value.isNotBlank()) {
                            categories=categories.map { g -> if(g.id==selectedId) g.copy(name=value) else g }
                            saveSettings()
                        }
                    },label={Text("Название")},singleLine=true)
                    Spacer(Modifier.height(12.dp))
                    Text("Значок")
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        GroupIcons.forEach { option ->
                            IconButton(onClick={symbol=option},
                                modifier=Modifier.border(if(symbol==option)2.dp else 0.dp,
                                    if(symbol==option) Color(0xFF238C78) else Color.Transparent,CircleShape)) {
                                Icon(glyph(option),option)
                            }
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={
                            val clean=name.trim()
                            if(clean.isNotEmpty()) {
                                categories=categories.map{
                                    if(it.id==selectedId) it.copy(name=clean,symbol=symbol) else it
                                }
                                saveSettings()
                            }
                        }) {Text("Сохранить")}
                        if(selectedId!="all") TextButton(onClick={
                            categories=categories.filterNot{it.id==selectedId}
                            membership=membership.filterValues{it!=selectedId}
                            onSelect("all")
                            selectGroup(categories.first())
                            saveSettings()
                        }){Text("Удалить")}
                    }
                    HorizontalDivider()
                    var newName by remember {mutableStateOf("")}
                    OutlinedTextField(newName,{newName=it},label={Text("Новая группа")},singleLine=true)
                    TextButton(onClick={
                        if(newName.trim().isNotEmpty()) {
                            val g=AppGroup(System.currentTimeMillis().toString(),newName.trim(),"folder")
                            categories=categories+g;selectGroup(g);onSelect(g.id)
                            newName="";saveSettings()
                        }
                    }) {Icon(Icons.Default.Add,null);Text("Создать группу")}
                }
            },
            confirmButton={TextButton(onClick=onDismiss){Text("Готово")}}
        )
    }
}
