import os
import secrets
import sqlite3
import time
from contextlib import contextmanager
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

DB_PATH = os.environ.get("VKUS_FEED_DB", "family_feed.db")
app = FastAPI(title="Vkus Detstva Family Feed", version="0.1.0")

@contextmanager
def db():
    con = sqlite3.connect(DB_PATH)
    con.row_factory = sqlite3.Row
    try:
        yield con
        con.commit()
    finally:
        con.close()

def init():
    with db() as con:
        con.executescript("""
        CREATE TABLE IF NOT EXISTS families(code TEXT PRIMARY KEY, created_at INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS posts(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            family_code TEXT NOT NULL,
            client_key TEXT NOT NULL,
            kind TEXT NOT NULL,
            title TEXT NOT NULL,
            body TEXT NOT NULL DEFAULT '',
            author TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL,
            UNIQUE(family_code, client_key)
        );
        CREATE TABLE IF NOT EXISTS likes(
            family_code TEXT NOT NULL,
            post_id INTEGER NOT NULL,
            device_id TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            PRIMARY KEY(family_code, post_id, device_id)
        );
        """)
init()

class PostIn(BaseModel):
    client_key: str = Field(min_length=1, max_length=100)
    kind: str = Field(pattern="^(recipe|moment)$")
    title: str = Field(min_length=1, max_length=200)
    body: str = Field(default="", max_length=4000)
    author: str = Field(default="", max_length=120)
    created_at: int | None = None

class LikeIn(BaseModel):
    device_id: str = Field(min_length=8, max_length=120)
    liked: bool = True

def ensure_family(con, code: str):
    if not con.execute("SELECT 1 FROM families WHERE code=?", (code,)).fetchone():
        raise HTTPException(status_code=404, detail="Family room not found")

@app.get("/health")
def health():
    return {"ok": True}

@app.post("/families")
def create_family():
    code = secrets.token_urlsafe(9)
    with db() as con:
        con.execute("INSERT INTO families(code, created_at) VALUES(?,?)", (code, int(time.time() * 1000)))
    return {"code": code}

@app.get("/families/{code}/posts")
def list_posts(code: str, limit: int = 100):
    with db() as con:
        ensure_family(con, code)
        rows = con.execute("""
            SELECT p.*, COUNT(l.device_id) AS likes
            FROM posts p LEFT JOIN likes l
              ON l.family_code=p.family_code AND l.post_id=p.id
            WHERE p.family_code=?
            GROUP BY p.id ORDER BY p.created_at DESC LIMIT ?
        """, (code, max(1, min(limit, 200)))).fetchall()
        return {"posts": [dict(r) for r in rows]}

@app.post("/families/{code}/posts")
def publish_post(code: str, post: PostIn):
    with db() as con:
        ensure_family(con, code)
        ts = post.created_at or int(time.time() * 1000)
        con.execute("""
            INSERT INTO posts(family_code,client_key,kind,title,body,author,created_at)
            VALUES(?,?,?,?,?,?,?)
            ON CONFLICT(family_code,client_key) DO UPDATE SET
              title=excluded.title, body=excluded.body, author=excluded.author, created_at=excluded.created_at
        """, (code, post.client_key, post.kind, post.title, post.body, post.author, ts))
        row = con.execute("SELECT id FROM posts WHERE family_code=? AND client_key=?", (code, post.client_key)).fetchone()
        return {"id": row["id"]}

@app.post("/families/{code}/posts/{post_id}/like")
def like_post(code: str, post_id: int, like: LikeIn):
    with db() as con:
        ensure_family(con, code)
        if not con.execute("SELECT 1 FROM posts WHERE family_code=? AND id=?", (code, post_id)).fetchone():
            raise HTTPException(status_code=404, detail="Post not found")
        if like.liked:
            con.execute("INSERT OR IGNORE INTO likes(family_code,post_id,device_id,created_at) VALUES(?,?,?,?)",
                        (code, post_id, like.device_id, int(time.time() * 1000)))
        else:
            con.execute("DELETE FROM likes WHERE family_code=? AND post_id=? AND device_id=?",
                        (code, post_id, like.device_id))
        count = con.execute("SELECT COUNT(*) c FROM likes WHERE family_code=? AND post_id=?", (code, post_id)).fetchone()["c"]
        return {"likes": count}
