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

import com.intellij.icons.AllIcons
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.tang.intellij.lua.debugger.LuaXNumberPresentation
import com.tang.intellij.lua.debugger.LuaXStringPresentation
import com.tang.intellij.lua.debugger.LuaXValuePresentation
import com.tang.intellij.lua.highlighting.LuaHighlightingData
import org.luaj.vm2.*
import javax.swing.Icon

/**
 *
 * Created by tangzx on 2017/4/16.
 */
class LuaRPrimitive(name: String) : LuaRValue(name) {
    private var type: String? = null
    private lateinit var data: String
    private var valuePresentation: XValuePresentation? = null
    private var icon: Icon? = AllIcons.Nodes.Variable
    override fun parse(data: LuaValue, desc: String) {
        this.data = data.toString()
        when (data) {
            is LuaString -> {
                type = "string"
                valuePresentation = LuaXStringPresentation(this.data)
                icon = AllIcons.Nodes.Static
            }
            is LuaNumber -> {
                type = "number"
                valuePresentation = LuaXNumberPresentation(this.data)
                icon = AllIcons.Nodes.Interface
            }
            is LuaBoolean -> {
                type = "boolean"
                valuePresentation = LuaXValuePresentation(type!!, this.data, LuaHighlightingData.PRIMITIVE_TYPE)
                icon = AllIcons.Nodes.ClassInitializer
            }
            is LuaFunction -> {
                type = "function"
                icon = AllIcons.Nodes.Function
            }
        }
    }

    override fun computePresentation(xValueNode: XValueNode, xValuePlace: XValuePlace) {
        if (valuePresentation == null) {
            xValueNode.setPresentation(icon, type, data, false)
        } else {
            xValueNode.setPresentation(icon, valuePresentation!!, false)
        }
    }
}
