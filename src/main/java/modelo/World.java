/*
* Fichero: World.java
*
* Autor: Manuel Enrique Jeronimo Aragon
*
* Descripcion:
* Define la clase World, contiene un mundo concreto mediante su ruta completa y el nombre de su carpeta.
* */

package modelo;

import java.util.Objects;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class World {
    private String worldDir;
    private String worldName;

    public World() {
        this.worldDir = "";
        this.worldName = MinecraftConstants.DEFAULT_WORLD_NAME;
    }

    public World(String worldDir, String worldName) {
        this.worldDir = worldDir == null ? "" : worldDir;
        setWorldName(worldName);
    }

    public void setWorldName(String worldName) {
        this.worldName = (worldName == null || worldName.isBlank())
                ? MinecraftConstants.DEFAULT_WORLD_NAME
                : worldName;
    }

    @Override
    public String toString() {
        return worldName;
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) return true;
        if(!(obj instanceof World other)) return false;
        return Objects.equals(worldDir, other.worldDir)
                && Objects.equals(worldName, other.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldDir, worldName);
    }
}
