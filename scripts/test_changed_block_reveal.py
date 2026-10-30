"""Timer/lifecycle regression test against production code and minimal API stubs."""
import ast
import subprocess
import tempfile
from pathlib import Path
root = Path(__file__).resolve().parents[1]
module = ast.parse((root / 'scripts/test_moving_piston_states.py').read_text())
stubs = ast.literal_eval(next(n.value for n in module.body if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'STUBS' for t in n.targets)))
stubs.pop('CacheTest.java')
stubs.update({
'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;
import net.minecraft.client.multiplayer.ClientLevel;
public class Minecraft {
 private static final Minecraft INSTANCE=new Minecraft();
 private final Thread owner=Thread.currentThread();
 public ClientLevel level; public boolean paused;
 public final Extractor levelExtractor=new Extractor();
 public static Minecraft getInstance(){return INSTANCE;}
 public boolean isSameThread(){return owner==Thread.currentThread();}
 public boolean isPaused(){return paused;}
 public static class Extractor {public int rebuilds; public void setBlocksDirty(int a,int b,int c,int d,int e,int f){rebuilds++;}}
}''',
'net/minecraft/core/BlockPos.java': '''package net.minecraft.core;
public record BlockPos(int x,int y,int z){public BlockPos immutable(){return this;} public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;}}''',
'net/minecraft/world/level/block/state/BlockState.java': 'package net.minecraft.world.level.block.state; public record BlockState(int id){public boolean isAir(){return id==0;}}',
'com/google/gson/Gson.java': 'package com.google.gson; public class Gson {public <T>T fromJson(String s,Class<T> c){return null;} public String toJson(Object o){return "{}";}}',
'net/fabricmc/loader/api/FabricLoader.java': '''package net.fabricmc.loader.api;
public class FabricLoader {public static FabricLoader getInstance(){return new FabricLoader();} public java.nio.file.Path getConfigDir(){return java.nio.file.Path.of(System.getProperty("test.config"));}}''',
'com/ruok0214/renderhide/RenderHideClient.java': '''package com.ruok0214.renderhide;
public class RenderHideClient {public static final Log LOGGER=new Log(); public static class Log {public void error(String s,Exception e){throw new AssertionError(e);}}}''',
'com/ruok0214/renderhide/RegionManager.java': '''package com.ruok0214.renderhide;
public class RegionManager {public static boolean global=true; public static boolean isInsideActiveRegion(net.minecraft.core.BlockPos p){return global && p.getX()<100;} public static float hiddenBlockOpacity(){return 0.5f;} public static boolean isGloballyEnabled(){return global;}}''',
'RevealTest.java': '''import com.ruok0214.renderhide.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
public class RevealTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  var c=Minecraft.getInstance();c.level=new ClientLevel();
  var p=new BlockPos(0,64,0);var a=new BlockState(1);var b=new BlockState(2);
  ChangedBlockReveal.tick(c);
  ChangedBlockReveal.changed(c.level,p,a,b);check(!ChangedBlockReveal.isRevealed(p));
  ChangedBlockReveal.configure(true,0.1);
  ChangedBlockReveal.changed(c.level,p,a,a);check(!ChangedBlockReveal.isRevealed(p));
  ChangedBlockReveal.changed(c.level,p,null,b);check(!ChangedBlockReveal.isRevealed(p));
  ChangedBlockReveal.changed(c.level,p,a,b);check(ChangedBlockReveal.isRevealed(p));
  ChangedBlockReveal.tick(c);check(ChangedBlockReveal.isRevealed(p));
  ChangedBlockReveal.changed(c.level,p,b,a);
  ChangedBlockReveal.tick(c);check(ChangedBlockReveal.isRevealed(p));
  c.paused=true;ChangedBlockReveal.tick(c);check(ChangedBlockReveal.isRevealed(p));c.paused=false;
  int before=c.levelExtractor.rebuilds;
  ChangedBlockReveal.tick(c);check(!ChangedBlockReveal.isRevealed(p));check(c.levelExtractor.rebuilds>before);
  ChangedBlockReveal.changed(c.level,p,a,b);ChangedBlockReveal.configure(false,2);check(!ChangedBlockReveal.isRevealed(p));
  ChangedBlockReveal.configure(true,2);
  ChangedBlockReveal.changed(c.level,p,a,b);ChangedBlockReveal.changed(c.level,p,b,new BlockState(0));check(!ChangedBlockReveal.isRevealed(p));
  var outside=new BlockPos(101,64,0);ChangedBlockReveal.changed(c.level,outside,a,b);check(!ChangedBlockReveal.isRevealed(outside));
  Thread t=new Thread(()->ChangedBlockReveal.changed(c.level,p,a,b));t.start();t.join();check(!ChangedBlockReveal.isRevealed(p));
  ChangedBlockReveal.changed(c.level,p,a,b);ChangedBlockReveal.removeChunk(c.level,new ChunkPos(0,0));check(!ChangedBlockReveal.isRevealed(p));
  ChangedBlockReveal.changed(c.level,p,a,b);c.level=new ClientLevel();ChangedBlockReveal.tick(c);check(!ChangedBlockReveal.isRevealed(p));
  ChangedBlockReveal.configure(true,Double.NaN);check(ChangedBlockReveal.seconds()==2);
  ChangedBlockReveal.changed(c.level,p,a,b);RegionManager.global=false;ChangedBlockReveal.tick(c);check(!ChangedBlockReveal.isRevealed(p));
  System.out.println("PASS: reveal, repeat/reset, expiry rebuild, pause, toggle, removal, region boundary, thread guard, chunk/world reset, duration validation");
 }
}'''
})
with tempfile.TemporaryDirectory() as directory:
 tmp=Path(directory);files=[]
 for name,content in stubs.items():
  p=tmp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content);files.append(str(p))
 subprocess.run(['javac','-d',str(tmp),str(root/'src/client/java/com/ruok0214/renderhide/ChangedBlockReveal.java'),*files],check=True)
 subprocess.run(['java','-Dtest.config='+str(tmp/'config'),'-cp',str(tmp),'RevealTest'],check=True)
