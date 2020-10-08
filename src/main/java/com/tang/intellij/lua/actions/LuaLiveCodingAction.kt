package com.tang.intellij.lua.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiFile
import com.intellij.ui.awt.RelativePoint
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.project.LuaSettings
import java.awt.Color
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.swing.Icon


class KeyboardState {
companion object
    {
        var shiftDown = false
        var altDown = false

    }
}

class LuaLiveCodingAction : ToggleAction("Lua Live Coding") {
    override fun isSelected(actionEvent: AnActionEvent): Boolean {
        return enabled
    }

    override fun setSelected(actionEvent: AnActionEvent, state: Boolean) {
        enabled = state


    }

    companion object {
        private var enabled = false

        init {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().
            addKeyEventPostProcessor { e: KeyEvent ->
                if (e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_SHIFT)
                {
                    KeyboardState.shiftDown = true
                }
                if (e.id == KeyEvent.KEY_RELEASED && e.keyCode == KeyEvent.VK_SHIFT)
                {
                    KeyboardState.shiftDown = false
                }
                if (e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_ALT)
                {
                    KeyboardState.altDown = true
                }
                if (e.id == KeyEvent.KEY_RELEASED && e.keyCode == KeyEvent.VK_ALT)
                {
                    KeyboardState.altDown = false
                }
                true
            }
        }

        fun sendCommand(filename: String, project: Project, psiFile: PsiFile) {
            if (!enabled) return

            val clientSocket = DatagramSocket()
            var sendData = "SAVE|$filename".toByteArray()
            var udpserver = LuaSettings.instance.reverseServer
            if (udpserver.contains("127.0.0.1") || udpserver.contains("localhost")) {
                //local pc
            } else {
                //remote mobile
                sendData = "SAVE|$filename|${psiFile.originalFile.text}".toByteArray()
            }

            val sendPacket = DatagramPacket(sendData, sendData.size, InetAddress.getByName(udpserver), 8090)
            clientSocket.send(sendPacket)
            clientSocket.close()

            showBalloon(project, LuaIcons.LIVE_TIP)
        }

        fun showBalloon(project: Project, icon: Icon) {
            val statusBar = WindowManager.getInstance().getIdeFrame(project)
//            val statusBar = WindowManager.getInstance().getStatusBar(project)
            var balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("", icon, MessageType.INFO.popupBackground, null)
                    .setHideOnAction(true)
                    .setHideOnClickOutside(true)
                    .setHideOnLinkClick(true)
                    .setHideOnKeyOutside(true)
            balloonBuilder.setFillColor(Color(100, 100, 100, 0))
            balloonBuilder.setBorderColor(Color(100, 100, 100, 0))
            balloonBuilder.setFadeoutTime(1000)
            val balloon = balloonBuilder.createBalloon()
            if (statusBar != null) {
                balloon.show(RelativePoint.getCenterOf(statusBar.component), Balloon.Position.above)
            }
        }

    }
}