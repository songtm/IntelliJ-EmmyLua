package com.tang.intellij.lua.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiManager
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.LuaFileUtil
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress


class LuaOpenActiveUIFile : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val clientSocket = DatagramSocket()
        clientSocket.soTimeout = 500
        val sendData = "ACTIVE_UI|none".toByteArray()
        val sendPacket = DatagramPacket(sendData, sendData.size, InetAddress.getByName(LuaSettings.instance.reverseServer), 8090)
        clientSocket.send(sendPacket)
        val recvPacket = DatagramPacket(ByteArray(1024), 1024)
        try {
            clientSocket.receive(recvPacket)
            val file = String(recvPacket.data, 0, recvPacket.length)
            println(file)
            for (project in ProjectManager.getInstance().openProjects) {
                val f = LuaFileUtil.findFile(project, file)
                if (f != null)
                    FileEditorManager.getInstance(project).openFile(f, true)
            }

        } catch (e: Exception) {
            println("wait file name time out")
        }
        clientSocket.close()
    }
}