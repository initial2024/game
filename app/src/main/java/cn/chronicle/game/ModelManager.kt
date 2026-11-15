package cn.chronicle.game
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
class ModelManager(context:Context) {
 val isLoaded=false
 suspend fun narrate(context:String):String=error("本版尚未加载推理后端")
 fun cancel() {}
 fun unloadAsync() {}
 fun close() {}
}
@Composable fun ModelPanel(vm:GameViewModel) { Text("本地 AI：当前使用规则叙事。模型接入正在开发，未提供模拟输出。") }
