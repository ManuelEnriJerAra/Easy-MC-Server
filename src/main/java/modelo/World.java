/*
* Fichero: World.java
*
* Autor: Manuel Enrique Jeronimo Aragon
*
* Descripcion:
* Define la clase World, contiene la informacion de un mundo concreto y su metadata asociada.
* */

package modelo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class World {
    private String worldDir;
    private String worldName;
    private String displayName;
    private String levelSeed;
    private String levelType;
    private Boolean generateStructures;
    private Boolean hardcore;
    private String gamemode;
    private String difficulty;
    private Boolean allowNether;

    public World() {
        this.worldDir = "";
        this.worldName = MinecraftConstants.DEFAULT_WORLD_NAME;
        this.displayName = MinecraftConstants.DEFAULT_WORLD_NAME;
        this.levelSeed = "";
        this.levelType = MinecraftConstants.DEFAULT_WORLD_TYPE;
        this.generateStructures = true;
        this.hardcore = false;
        this.gamemode = MinecraftConstants.DEFAULT_GAMEMODE;
        this.difficulty = MinecraftConstants.DEFAULT_DIFFICULTY;
        this.allowNether = true;
    }

    public World(String worldDir, String worldName) {
        this();
        this.worldDir = worldDir;
        setWorldName(worldName);
    }

    public void setWorldName(String worldName) {
        if(worldName == null || worldName.isBlank()) {
            this.worldName = MinecraftConstants.DEFAULT_WORLD_NAME;
            if(this.displayName == null || this.displayName.isBlank()) {
                this.displayName = this.worldName;
            }
            return;
        }

        this.worldName = worldName;
        if(this.displayName == null || this.displayName.isBlank()) {
            this.displayName = worldName;
        }
    }

    public void setDisplayName(String displayName) {
        if(displayName == null || displayName.isBlank()) {
            this.displayName = worldName == null || worldName.isBlank() ? MinecraftConstants.DEFAULT_WORLD_NAME : worldName;
            return;
        }
        this.displayName = displayName;
    }

    public String getDisplayNameOrWorldName() {
        if(displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return worldName == null || worldName.isBlank() ? MinecraftConstants.DEFAULT_WORLD_NAME : worldName;
    }
}
