package com.areyouasleep.tile

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.areyouasleep.data.Phase
import com.areyouasleep.core.GuardState
import com.areyouasleep.service.SleepGuardService

/**
 * 下拉快捷设置里的磁贴：一下开，一下关。
 */
class GuardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        when (GuardState.phase.value) {
            Phase.IDLE, Phase.DONE -> SleepGuardService.start(this)
            else -> SleepGuardService.stop(this)
        }
        // 服务状态是异步切换的，稍等一下再刷新磁贴
        Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 500L)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val phase = GuardState.phase.value
        tile.state = when (phase) {
            Phase.ARMED, Phase.PAUSED -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = "睡了吗"
        // subtitle 是 API 29 才有的，8.x / 9 上就只显示应用名
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (phase) {
                Phase.ARMED -> "守护中"
                Phase.PAUSED -> "已暂停"
                Phase.DONE -> "已记录"
                Phase.IDLE -> "未启动"
            }
        }
        tile.updateTile()
    }
}
