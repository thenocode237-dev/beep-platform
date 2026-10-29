package io.beep.bo.app.port;

/** Taille de la base du back office (la base de BeepClick est mesurée par le core). */
public interface DatabaseStatsPort {
  long databaseBytes();
}
