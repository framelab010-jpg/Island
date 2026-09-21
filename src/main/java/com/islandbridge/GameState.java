package com.islandbridge;

public enum GameState {
    /** Menunggu permainan dimulakan. */
    LOBBY,
    /** Permainan berjalan seperti biasa. */
    RUNNING,
    /** Mesyuarat / undian sedang berlangsung. */
    MEETING,
    /** Permainan baru tamat, sedang reset. */
    ENDED
}
