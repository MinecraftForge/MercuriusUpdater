package net.minecraftforge.mercurius.updater;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.mercurius.updater.Constants;
import net.minecraftforge.mercurius.updater.LogHelper;
import net.minecraftforge.mercurius.updater.Utils;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.ReflectionHelper.UnableToFindMethodException;
import cpw.mods.fml.relauncher.Side;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;

@Mod(modid = Constants.MODID, version = Constants.VERSION)
public class MercuriusUpdaterMod
{
    @SuppressWarnings("rawtypes")
    private Class loadedMercurius;
    private Object loadedMercuriusInstance;

    @EventHandler
    public void preInit(FMLPreInitializationEvent e)
    {
        if (Loader.isModLoaded("mercurius"))
        {
            LogHelper.info("Normal Mercurius found, disabeling stub");
            return;
        }

        File librariesDir = Utils.findMaven(MinecraftForge.class, e.getSide() == Side.CLIENT);
        if (librariesDir == null)
            return;

        File libFile = Utils.updateMercurius(librariesDir, Loader.MC_VERSION);
        if (libFile == null)
        {
            LogHelper.fatal("Mercurius Updating failed");
            return;
        }


        URLClassLoader cl;
        try
        {
            cl = URLClassLoader.newInstance(new URL[] {libFile.toURI().toURL()}, MercuriusUpdaterMod.class.getClassLoader());
            loadedMercurius = cl.loadClass("net.minecraftforge.mercurius.StatsMod");
            loadedMercuriusInstance = loadedMercurius.newInstance();
        }
        catch (Exception e1)
        {
            e1.printStackTrace();
        }

        invokeEvent("preInit", e);
    }

    @EventHandler
    public void init(FMLInitializationEvent e)
    {
        invokeEvent("init", e);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent e)
    {
        invokeEvent("postInit", e);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent e)
    {
        invokeEvent("serverStarting", e);
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent e)
    {
        invokeEvent("serverStopping", e);
    }

    @SuppressWarnings("unchecked")
    private void invokeEvent(String name, FMLEvent event)
    {
        if (loadedMercurius == null)
            return;

        try {
            ReflectionHelper.findMethod(loadedMercurius, loadedMercuriusInstance, new String[]{ name }, event.getClass())
                .invoke(loadedMercuriusInstance, event);
        } catch (UnableToFindMethodException e) {
            // No method found so its not listening for it.
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
