package model;

/**
 * Created by remibengtpettersen on 12.02.2016.
 */
public interface Rule {

    boolean shouldLive(int neighbours);

    boolean shouldDie(int neighbours);
}
