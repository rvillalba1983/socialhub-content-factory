package com.socialhub.production.domain;

/** Estado propio de una estación (independiente del de sus tareas). */
public enum StatusLevel { OK, RUNNING, WAITING, BLOCKED, FAILED, IDLE, CANCELLED }
