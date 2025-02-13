package li.songe.gkd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.AlertDialog
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SimpleTopAppBar
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.subsIdToRawFlow

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun AppItemPage(
    subsItemId: Long,
    appId: String,
    focusGroupKey: Int? = null, // 背景/边框高亮一下
) {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val vm = hiltViewModel<AppItemVm>()
    val subsItem by vm.subsItemFlow.collectAsState()
    val subsConfigs by vm.subsConfigsFlow.collectAsState()
    val appRaw by vm.subsAppFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    val appRawVal = appRaw
    val subsItemVal = subsItem

    val (showGroupItem, setShowGroupItem) = remember {
        mutableStateOf<SubscriptionRaw.GroupRaw?>(
            null
        )
    }

    val editable = subsItemId < 0

    var showAddDlg by remember { mutableStateOf(false) }

    val (menuGroupRaw, setMenuGroupRaw) = remember {
        mutableStateOf<SubscriptionRaw.GroupRaw?>(null)
    }
    val (editGroupRaw, setEditGroupRaw) = remember {
        mutableStateOf<SubscriptionRaw.GroupRaw?>(null)
    }

    Scaffold(topBar = {
        SimpleTopAppBar(
            onClickIcon = { navController.popBackStack() },
            title = if (subsItem == null) "订阅文件缺失" else (appInfoCache[appRaw?.id]?.name
                ?: appRaw?.name ?: appRaw?.id ?: "")
        )
    }, floatingActionButton = {
        if (editable) {
            FloatingActionButton(onClick = { showAddDlg = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "add",
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    },

        content = { contentPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                appRaw?.groups?.let { groupsVal ->
                    itemsIndexed(groupsVal, { i, g -> i.toString() + g.key }) { _, group ->
                        Row(
                            modifier = Modifier
                                .background(
                                    if (group.key == focusGroupKey) Color(0x500a95ff) else Color.Transparent
                                )
                                .clickable { setShowGroupItem(group) }
                                .padding(10.dp, 6.dp)
                                .fillMaxWidth()
                                .height(45.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = group.name,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (group.valid) {
                                    Text(
                                        text = group.desc ?: "-",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                        fontSize = 14.sp
                                    )
                                } else {
                                    Text(
                                        text = "规则组损坏",
                                        color = Color.Red,
                                        modifier = Modifier.fillMaxWidth(),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))

                            if (editable) {
                                IconButton(onClick = {
                                    setMenuGroupRaw(group)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "more",
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            val subsConfig = subsConfigs.find { it.groupKey == group.key }
                            Switch(checked = (subsConfig?.enable ?: group.enable) ?: true,
                                modifier = Modifier,
                                onCheckedChange = scope.launchAsFn { enable ->
                                    val newItem = (subsConfig?.copy(enable = enable) ?: SubsConfig(
                                        type = SubsConfig.GroupType,
                                        subsItemId = subsItemId,
                                        appId = appId,
                                        groupKey = group.key,
                                        enable = enable
                                    ))
                                    DbSet.subsConfigDao.insert(newItem)
                                })
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        })


    showGroupItem?.let { showGroupItemVal ->
        AlertDialog(modifier = Modifier.defaultMinSize(300.dp),
            onDismissRequest = { setShowGroupItem(null) },
            title = {
                Text(text = showGroupItemVal.name)
            },
            text = {
                Column {
                    if (showGroupItemVal.enable == false) {
                        Text(text = "该规则组默认不启用", color = Color.Blue)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Text(text = showGroupItemVal.desc ?: "-")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val groupAppText = Singleton.omitJson.encodeToString(
                        appRaw?.copy(
                            groups = listOf(showGroupItemVal)
                        )
                    )
                    ClipboardUtils.copyText(groupAppText)
                    ToastUtils.showShort("复制成功")
                }) {
                    Text(text = "复制规则组")
                }
            })
    }

    if (menuGroupRaw != null && appRawVal != null && subsItemVal != null) {
        Dialog(onDismissRequest = { setMenuGroupRaw(null) }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(10.dp)
                    .width(200.dp)
            ) {
                Text(text = "编辑", modifier = Modifier
                    .clickable {
                        setEditGroupRaw(menuGroupRaw)
                        setMenuGroupRaw(null)
                    }
                    .padding(10.dp)
                    .fillMaxWidth())
                Text(text = "删除", color = Color.Red, modifier = Modifier
                    .clickable {
                        vm.viewModelScope.launchTry(Dispatchers.IO) {
                            val subsRaw = subsIdToRawFlow.value[subsItemId] ?: return@launchTry
                            val newSubsRaw = subsRaw.copy(
                                apps = subsRaw.apps
                                    .toMutableList()
                                    .apply {
                                        set(indexOfFirst { a -> a.id == appRawVal.id },
                                            appRawVal.copy(groups = appRawVal.groups.filter { g -> g.key != menuGroupRaw.key })
                                        )
                                    })
                            subsItemVal.subsFile.writeText(
                                Singleton.json.encodeToString(
                                    newSubsRaw
                                )
                            )
                            DbSet.subsItemDao.update(subsItemVal.copy(mtime = System.currentTimeMillis()))
                            DbSet.subsConfigDao.delete(
                                subsItemVal.id, appRawVal.id, menuGroupRaw.key
                            )
                            ToastUtils.showShort("删除成功")
                            setMenuGroupRaw(null)
                        }
                    }
                    .padding(10.dp)
                    .fillMaxWidth())
            }
        }
    }

    if (editGroupRaw != null && appRawVal != null && subsItemVal != null) {
        var source by remember {
            mutableStateOf(Singleton.omitJson.encodeToString(editGroupRaw))
        }
        Dialog(onDismissRequest = { setEditGroupRaw(null) }) {
            Column(
                modifier = Modifier.defaultMinSize(minWidth = 300.dp),
            ) {
                Text(text = "编辑规则组", fontSize = 18.sp, modifier = Modifier.padding(10.dp))
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    placeholder = { Text(text = "请输入规则组") },
                    maxLines = 8,
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp)
                        .fillMaxWidth()
                ) {
                    TextButton(onClick = {
                        val newGroupRaw = try {
                            SubscriptionRaw.parseGroupRaw(source)
                        } catch (e: Exception) {
                            LogUtils.d(e)
                            ToastUtils.showShort("非法规则:${e.message}")
                            return@TextButton
                        }
                        if (newGroupRaw.key != editGroupRaw.key) {
                            ToastUtils.showShort("不能更改规则组的key")
                            return@TextButton
                        }
                        if (!newGroupRaw.valid) {
                            ToastUtils.showShort("非法规则:存在非法选择器")
                            return@TextButton
                        }
                        setEditGroupRaw(null)
                        val subsRaw = subsIdToRawFlow.value[subsItemId] ?: return@TextButton
                        val newSubsRaw = subsRaw.copy(apps = subsRaw.apps.toMutableList().apply {
                            set(indexOfFirst { a -> a.id == appRawVal.id },
                                appRawVal.copy(groups = appRawVal.groups.toMutableList().apply {
                                    set(
                                        indexOfFirst { g -> g.key == newGroupRaw.key }, newGroupRaw
                                    )
                                })
                            )
                        })
                        vm.viewModelScope.launchTry(Dispatchers.IO) {
                            subsItemVal.subsFile.writeText(
                                Singleton.json.encodeToString(
                                    newSubsRaw
                                )
                            )
                            DbSet.subsItemDao.update(subsItemVal.copy(mtime = System.currentTimeMillis()))
                            ToastUtils.showShort("更新成功")
                        }
                    }, enabled = source.isNotEmpty()) {
                        Text(text = "更新")
                    }
                }
            }
        }
    }

    if (showAddDlg && appRawVal != null && subsItemVal != null) {
        var source by remember {
            mutableStateOf("")
        }
        Dialog(onDismissRequest = { showAddDlg = false }) {
            Column(
                modifier = Modifier.defaultMinSize(minWidth = 300.dp),
            ) {
                Text(text = "添加规则组", fontSize = 18.sp, modifier = Modifier.padding(10.dp))
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    placeholder = { Text(text = "请输入规则组\n可以是APP规则\n也可以是单个规则组") },
                    maxLines = 8,
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp)
                        .fillMaxWidth()
                ) {
                    TextButton(onClick = {
                        val newAppRaw = try {
                            SubscriptionRaw.parseAppRaw(source)
                        } catch (_: Exception) {
                            null
                        }
                        val tempGroups = if (newAppRaw == null) {
                            val newGroupRaw = try {
                                SubscriptionRaw.parseGroupRaw(source)
                            } catch (e: Exception) {
                                LogUtils.d(e)
                                ToastUtils.showShort("非法规则:${e.message}")
                                return@TextButton
                            }
                            listOf(newGroupRaw)
                        } else {
                            if (newAppRaw.id != appRawVal.id) {
                                ToastUtils.showShort("id不一致,无法添加")
                                return@TextButton
                            }
                            if (newAppRaw.groups.isEmpty()) {
                                ToastUtils.showShort("不能添加空规则组")
                                return@TextButton
                            }
                            newAppRaw.groups
                        }
                        if (!tempGroups.all { g -> g.valid }) {
                            ToastUtils.showShort("非法规则:存在非法选择器")
                            return@TextButton
                        }
                        tempGroups.forEach { g ->
                            if (appRawVal.groups.any { g2 -> g2.name == g.name }) {
                                ToastUtils.showShort("存在同名规则[${g.name}]")
                                return@TextButton
                            }
                        }
                        val newKey = appRawVal.groups.maxBy { g -> g.key }.key + 1
                        val subsRaw = subsIdToRawFlow.value[subsItemId] ?: return@TextButton
                        val newSubsRaw = subsRaw.copy(apps = subsRaw.apps.toMutableList().apply {
                            set(indexOfFirst { a -> a.id == appRawVal.id },
                                appRawVal.copy(groups = appRawVal.groups + tempGroups.mapIndexed { i, g ->
                                    g.copy(
                                        key = newKey + i
                                    )
                                })
                            )
                        })
                        vm.viewModelScope.launchTry(Dispatchers.IO) {
                            subsItemVal.subsFile.writeText(Singleton.json.encodeToString(newSubsRaw))
                            DbSet.subsItemDao.update(subsItemVal.copy(mtime = System.currentTimeMillis()))
                            showAddDlg = false
                            ToastUtils.showShort("添加成功")
                        }
                    }, enabled = source.isNotEmpty()) {
                        Text(text = "添加")
                    }
                }
            }
        }
    }
}

