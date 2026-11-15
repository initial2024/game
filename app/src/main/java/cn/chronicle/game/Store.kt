package cn.chronicle.game

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import cn.chronicle.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.security.MessageDigest

@Serializable data class SaveEnvelope(val format:Int=1,val world:String,val history:List<Entry>,val sha256:String)
data class Slot(val id:Int,val name:String,val turn:Int,val revision:Long)
class Store(context:Context):SQLiteOpenHelper(context,"chronicle.db",null,1) {
 override fun onCreate(db:SQLiteDatabase) {
  db.execSQL("CREATE TABLE saves(slot INTEGER PRIMARY KEY,revision INTEGER NOT NULL,name TEXT NOT NULL,turn INTEGER NOT NULL,json TEXT NOT NULL)")
  db.execSQL("CREATE TABLE history(slot INTEGER NOT NULL,revision INTEGER NOT NULL,json TEXT NOT NULL,PRIMARY KEY(slot,revision))")
 }
 override fun onConfigure(db:SQLiteDatabase) { db.enableWriteAheadLogging() }
 override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int) { error("缺少数据库迁移 $old → $new") }
 fun slots():List<Slot> = readableDatabase.rawQuery("SELECT slot,name,turn,revision FROM saves ORDER BY slot",null).use { c -> buildList { while(c.moveToNext()) add(Slot(c.getInt(0),c.getString(1),c.getInt(2),c.getLong(3))) } }
 fun load(slot:Int):World? = readableDatabase.rawQuery("SELECT json FROM saves WHERE slot=?",arrayOf(slot.toString())).use { c -> if(c.moveToFirst()) SaveJson.decodeFromString<World>(c.getString(0)).also { Rules.verify(it) } else null }
 fun create(slot:Int,w:World) { require(slot in 1..8); Rules.verify(w); transaction { db ->
  require(db.rawQuery("SELECT 1 FROM saves WHERE slot=?",arrayOf(slot.toString())).use { !it.moveToFirst() }) { "该存档槽已占用。" }
  put(db,slot,w)
 } }
 fun commit(slot:Int,expected:Long,out:Outcome) { Rules.verify(out.world); transaction { db ->
  val current=db.rawQuery("SELECT revision FROM saves WHERE slot=?",arrayOf(slot.toString())).use { require(it.moveToFirst()); it.getLong(0) }
  require(current==expected && out.world.revision==expected+1) { "存档版本已改变，本次未执行。" }
  put(db,slot,out.world)
  db.insertOrThrow("history",null,ContentValues().apply { put("slot",slot);put("revision",out.entry.revision);put("json",SaveJson.encodeToString(out.entry)) })
 } }
 fun copyTo(from:Int,to:Int) { require(to in 1..8 && from!=to); transaction { db ->
  require(load(to)==null) { "目标槽已占用。" }; val w=load(from) ?: error("源存档不存在"); put(db,to,w)
  db.execSQL("INSERT INTO history(slot,revision,json) SELECT ?,revision,json FROM history WHERE slot=?",arrayOf(to,from))
 } }
 fun history(slot:Int,offset:Int=0,limit:Int=20):List<Entry> = readableDatabase.rawQuery("SELECT json FROM history WHERE slot=? ORDER BY revision DESC LIMIT ? OFFSET ?",arrayOf(slot.toString(),limit.coerceIn(1,10001).toString(),offset.toString())).use { c -> buildList { while(c.moveToNext()) add(SaveJson.decodeFromString<Entry>(c.getString(0))) } }
 fun export(slot:Int):String { val w=load(slot) ?: error("无存档"); val world=SaveJson.encodeToString(w); val h=history(slot,0,10001); require(h.size<=10000) { "本版导出支持一万次决策以内的存档。" }; val digest=sha(world+SaveJson.encodeToString(h)); return SaveJson.encodeToString(SaveEnvelope(world=world,history=h,sha256=digest)) }
 fun import(text:String,slot:Int) {
  require(text.toByteArray().size<=8*1024*1024) { "导入超过 8 MB。" }
  val e=SaveJson.decodeFromString<SaveEnvelope>(text);require(e.format==1); require(sha(e.world+SaveJson.encodeToString(e.history))==e.sha256) { "存档校验失败。" }
  val w=SaveJson.decodeFromString<World>(e.world);Rules.verify(w)
  require(e.history.size<=10000 && e.history.all { it.seed==w.seed && it.revision in 1..w.revision } && e.history.map { it.revision }.distinct().size==e.history.size)
  transaction { db -> require(load(slot)==null) { "请选择空槽导入。" }; put(db,slot,w);e.history.forEach { entry -> db.insertOrThrow("history",null,ContentValues().apply { put("slot",slot);put("revision",entry.revision);put("json",SaveJson.encodeToString(entry)) }) } }
 }
 private fun put(db:SQLiteDatabase,slot:Int,w:World) { db.insertWithOnConflict("saves",null,ContentValues().apply { put("slot",slot);put("revision",w.revision);put("name",w.name);put("turn",w.turn);put("json",SaveJson.encodeToString(w)) },SQLiteDatabase.CONFLICT_REPLACE) }
 private fun transaction(block:(SQLiteDatabase)->Unit) { val db=writableDatabase;db.beginTransaction();try { block(db);db.setTransactionSuccessful() } finally { db.endTransaction() } }
 companion object { fun sha(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }
}
