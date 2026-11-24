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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
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
    private var animPreset by mutableStateOf("Liquid")
    private var animMillis by mutableFloatStateOf(360f)
    private var springAmount by mutableFloatStateOf(.45f)

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        readSettings()
        refreshApps()
        setContent { MaterialTheme { LauncherScreen() } }
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
        if(obj.kind=="image" && obj.action!="none")
            installed.firstOrNull { it.pkg==obj.action }?.let(::openApp)
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
            animPreset=prefs.getString("animation","Liquid") ?: "Liquid"
            animMillis=prefs.getFloat("duration",360f)
            springAmount=prefs.getFloat("spring",.45f)
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
            .putFloat("spring",springAmount).apply()
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
        var groupEditor by remember { mutableStateOf(false) }
        var appMenu by remember { mutableStateOf<InstalledApp?>(null) }
        var settings by remember { mutableStateOf(false) }
        var themeMenu by remember { mutableStateOf(false) }
        var stickerAction by remember { mutableStateOf(false) }
        var pendingWidgetId by remember { mutableIntStateOf(-1) }
        var dragAmount by remember { mutableFloatStateOf(0f) }
        val configuration=LocalConfiguration.current
        val screenWidth=with(LocalDensity.current){configuration.screenWidthDp.dp.toPx()}
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
            .pointerInput(drawer,edit) {
                detectVerticalDragGestures(
                    onDragStart={dragAmount=0f},
                    onDragEnd={
                        if(dragAmount < -75f && !edit) { drawer=true;homeMenu=false }
                        if(dragAmount > 100f && drawer) drawer=false
                        dragAmount=0f
                    },
                    onVerticalDrag={ change,amount ->
                        if(!change.isConsumed) dragAmount+=amount
                    }
                )
            }
            .pointerInput(drawer,edit) {
                detectTapGestures(
                    onLongPress={ if(!drawer) {homeMenu=true;selected=null} },
                    onTap={ if(!drawer && !edit) homeMenu=false }
                )
            }) {
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
                        objectModifier=objectModifier.pointerInput(obj.id) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                selected=obj.id
                                objects=objects.map { item ->
                                    if(item.id==obj.id) item.copy(
                                        x=(item.x+pan.x).coerceIn(-screenWidth*.5f,screenWidth*1.3f),
                                        y=(item.y+pan.y).coerceAtLeast(-100f),
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
                            listOf(Alignment.TopStart,Alignment.TopEnd,Alignment.BottomStart,Alignment.BottomEnd).forEach { corner ->
                                Box(Modifier.align(corner).offset(
                                    x=if(corner==Alignment.TopStart || corner==Alignment.BottomStart) (-5).dp else 5.dp,
                                    y=if(corner==Alignment.TopStart || corner==Alignment.TopEnd) (-5).dp else 5.dp
                                ).size(10.dp).clip(CircleShape).background(GlassText))
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible=homeMenu && !drawer,
                enter=slideInVertically(animationSpec=tween(animMillis.toInt().coerceAtLeast(100))){ it / 2 }+fadeIn(),
                exit=slideOutVertically(animationSpec=tween(180)){it/2}+fadeOut(),
                modifier=Modifier.align(Alignment.BottomCenter).padding(start=12.dp,end=12.dp,bottom=30.dp)
            ) {
                LiquidPanel(Modifier.fillMaxWidth(),radius=28.dp) {
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
                modifier=Modifier.align(Alignment.BottomCenter).padding(14.dp),
                enter=slideInVertically(animationSpec=tween(260)){it}+fadeIn(),
                exit=slideOutVertically(animationSpec=tween(180)){it}+fadeOut()
            ) {
                LiquidPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,
                            verticalAlignment=Alignment.CenterVertically) {
                            SmallChoice(Icons.Default.AddPhotoAlternate,"Изображение"){imagePicker.launch(arrayOf("image/png","image/webp","image/*"))}
                            SmallChoice(Icons.Default.Widgets,"Виджет"){chooseWidget()}
                            SmallChoice(Icons.Default.Apps,"Приложение"){drawer=true}
                            SmallChoice(Icons.Default.Check,"Готово"){
                                edit=false;selected=null;saveSettings()
                            }
                        }
                        if(selectedObject!=null) {
                            Text(selectedObject.label,color=GlassText,fontSize=13.sp)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text("Размер",color=GlassText,fontSize=12.sp)
                                Slider(value=selectedObject.scale,onValueChange={new ->
                                    objects=objects.map {if(it.id==selectedObject.id) it.copy(scale=new) else it}
                                },valueRange=.25f..3.7f,modifier=Modifier.weight(1f))
                                TextButton(onClick={selectedObject.let(::deleteObject);selected=null}) {
                                    Icon(Icons.Default.Delete,"Удалить",tint=GlassText)
                                }
                                if(selectedObject.kind=="image") {
                                    IconButton(onClick={stickerAction=true}) {Icon(Icons.Default.TouchApp,"Действие",tint=GlassText)}
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible=drawer,
                enter=slideInVertically(animationSpec=tween(animMillis.toInt().coerceAtLeast(100))){it}+fadeIn(),
                exit=slideOutVertically(animationSpec=tween(230)){it}+fadeOut()
            ) {
                Box(Modifier.fillMaxSize().padding(top=32.dp)) {
                    LiquidPanel(
                        Modifier.fillMaxSize().padding(start=5.dp,end=5.dp,bottom=3.dp),radius=34.dp
                    ) {
                        Column(Modifier.fillMaxSize().padding(top=24.dp,start=14.dp,end=14.dp,bottom=22.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text("Приложения",color=GlassText,fontSize=29.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                                IconButton(onClick={groupEditor=true}) {Icon(Icons.Default.Edit,"Редактировать группы",tint=GlassText)}
                            }
                            OutlinedTextField(
                                value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),
                                singleLine=true,
                                leadingIcon={Icon(Icons.Default.Search,"Поиск",tint=GlassText)},
                                placeholder={Text("Поиск приложений",color=GlassText.copy(alpha=.7f))},
                                colors=OutlinedTextFieldDefaults.colors(
                                    focusedTextColor=GlassText,unfocusedTextColor=GlassText,
                                    focusedBorderColor=GlassText.copy(alpha=.7f),
                                    unfocusedBorderColor=GlassText.copy(alpha=.45f),
                                    cursorColor=GlassText
                                ),shape=RoundedCornerShape(22.dp)
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                categories.forEach { group ->
                                    val active=groupId==group.id
                                    LiquidPanel(
                                        Modifier.widthIn(min=75.dp).clip(RoundedCornerShape(22.dp))
                                            .clickable {groupId=group.id},radius=20.dp
                                    ) {
                                        Column(
                                            Modifier.background(if(active) Color.White.copy(alpha=.24f) else Color.Transparent)
                                                .padding(horizontal=12.dp,vertical=10.dp),
                                            horizontalAlignment=Alignment.CenterHorizontally
                                        ) {
                                            Icon(glyph(group.symbol),contentDescription=null,tint=GlassText,modifier=Modifier.size(23.dp))
                                            Spacer(Modifier.height(4.dp))
                                            Text(group.name,color=GlassText,fontSize=11.sp,maxLines=1)
                                        }
                                    }
                                }
                                IconButton(onClick={groupEditor=true}) {
                                    Icon(Icons.Default.AddCircleOutline,"Добавить группу",tint=GlassText)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            val list=filteredApps(groupId,search)
                            LazyVerticalGrid(
                                GridCells.Fixed(4),modifier=Modifier.weight(1f),
                                verticalArrangement=Arrangement.spacedBy(19.dp),
                                horizontalArrangement=Arrangement.spacedBy(5.dp),
                                contentPadding=PaddingValues(bottom=16.dp)
                            ) {
                                items(list,key={it.key}) { app ->
                                    Column(
                                        Modifier.fillMaxWidth().combinedClickable(
                                            onClick={openApp(app)},
                                            onLongClick={appMenu=app}
                                        ),
                                        horizontalAlignment=Alignment.CenterHorizontally
                                    ) {
                                        AppIcon(app.pkg,Modifier.size(58.dp))
                                        Spacer(Modifier.height(5.dp))
                                        Text(app.label,color=GlassText,fontSize=11.sp,
                                            maxLines=2,overflow=TextOverflow.Ellipsis,
                                            lineHeight=12.sp)
                                    }
                                }
                            }
                        }
                    }
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
                        Text("Прозрачность стекла: "+(glassOpacity*100).roundToInt()+"%")
                        Slider(glassOpacity,{glassOpacity=it;saveSettings()},valueRange=.25f..1f)
                        Spacer(Modifier.height(8.dp))
                        Text("Набор анимаций",fontWeight=FontWeight.Medium)
                        listOf("Liquid","Spring","Smooth","Dynamic").forEach { p ->
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
                text={Text("Стеклянные панели используют цвета обоев. Выбери новые обои или настрой прозрачность стекла в параметрах лаунчера.")},
                confirmButton={TextButton(onClick={themeMenu=false;settings=true}){Text("Настройки")}},
                dismissButton={TextButton(onClick={themeMenu=false}){Text("Закрыть")}}
            )
        }
        if(stickerAction && selectedObject!=null) {
            AlertDialog(
                onDismissRequest={stickerAction=false},
                title={Text("Действие изображения")},
                text={
                    Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {
                        TextButton(onClick={
                            objects=objects.map{if(it.id==selectedObject.id) it.copy(action="none") else it}
                            saveSettings();stickerAction=false
                        }){Text("Без действия (касания проходят сквозь изображение)")}
                        installed.forEach { app ->
                            TextButton(onClick={
                                objects=objects.map{if(it.id==selectedObject.id) it.copy(action=app.pkg) else it}
                                saveSettings();stickerAction=false
                            }){Text(app.label)}
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
                    OutlinedTextField(name,{name=it},label={Text("Название")},singleLine=true,enabled=selectedId!="all")
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
                            if(selectedId!="all" && clean.isNotEmpty()) {
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
