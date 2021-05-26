/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.debugger.remote.value

import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.psi.LuaFileUtil
import org.jetbrains.concurrency.Promise
import org.luaj.vm2.LuaValue

/**
 *
 * Created by tangzx on 2017/4/16.
 */
class LuaRFunction(name: String) : LuaRTable(name) {
    private var type = "function"
    private lateinit var sourcePos: String
    private lateinit var desc: String
    private var canNavigate2lua = false
    override fun parse(data: LuaValue, desc: String) {
        this.sourcePos = data.call().toString()
        this.desc = desc
        this.canNavigate2lua = sourcePos.contains(".lua")
    }

    override fun computePresentation(xValueNode: XValueNode, xValuePlace: XValuePlace) {
        val last = sourcePos.split("/").last()
        val des = if (last == "nil") desc else "$desc ($last)"
        xValueNode.setPresentation(LuaIcons.LOCAL_FUNCTION, type, des, canNavigate2lua)
    }

    override fun canNavigateToTypeSource(): Boolean {
        return canNavigate2lua
    }
    override fun computeTypeSourcePosition(navigatable: XNavigatable) {
        val virtualFile = LuaFileUtil.findFile(session.project, sourcePos.split(":").first())
        val position = XSourcePositionImpl.create(virtualFile, sourcePos.split(":").last().toInt() - 1)//0行开始??
        navigatable.setSourcePosition(position)
    }
}
