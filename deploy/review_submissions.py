#!/usr/bin/env python3
"""
Manual review tool for user-submitted decks, PMVs, and cards.

Runs outside the game server — intended for server admins with DB access.
Approves or rejects pending rows in cof_deck / cof_deck_pmv / cof_card.

Usage:
  export DATABASE_URL=postgresql://postgres:123123@127.0.0.1:5432/cof_db
  python3 deploy/review_submissions.py

Requires: psycopg2-binary (pip install -r deploy/requirements.txt)
"""

from __future__ import annotations

import os
import sys
from typing import Any

try:
    import psycopg2
    import psycopg2.extras
except ImportError:
    print("Install dependencies: pip install -r deploy/requirements.txt", file=sys.stderr)
    sys.exit(1)

DATABASE_URL = os.environ.get(
    "DATABASE_URL",
    "postgresql://postgres:123123@127.0.0.1:5432/cof_db",
)


def connect():
    return psycopg2.connect(DATABASE_URL)


def fetch_pending(conn) -> dict[str, list[dict[str, Any]]]:
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            """
            SELECT id, folder_name, name, submitter_client_id, review_status, card_count, pmv_count, updated_at
            FROM cof_deck WHERE review_status = 'pending' ORDER BY updated_at DESC
            """
        )
        decks = list(cur.fetchall())
        cur.execute(
            """
            SELECT id, deck_id, pmv_id, name, author, submitter_client_id, review_status
            FROM cof_deck_pmv WHERE review_status = 'pending' ORDER BY deck_id, pmv_id
            """
        )
        pmvs = list(cur.fetchall())
        cur.execute(
            """
            SELECT id, deck_id, pmv_id, card_id, shot, image_url, submitter_client_id, review_status
            FROM cof_card WHERE review_status = 'pending' ORDER BY deck_id, pmv_id, card_id
            """
        )
        cards = list(cur.fetchall())
    return {"decks": decks, "pmvs": pmvs, "cards": cards}


def print_section(title: str, rows: list[dict[str, Any]], formatter) -> None:
    print(f"\n=== {title} ({len(rows)}) ===")
    if not rows:
        print("  (none)")
        return
    for row in rows:
        print(" ", formatter(row))


def approve_deck(conn, deck_id: int) -> None:
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE cof_deck SET review_status = 'approved', enabled = TRUE, updated_at = EXTRACT(EPOCH FROM NOW()) * 1000
            WHERE id = %s
            """,
            (deck_id,),
        )
        cur.execute(
            "UPDATE cof_deck_pmv SET review_status = 'approved' WHERE deck_id = %s AND review_status = 'pending'",
            (deck_id,),
        )
        cur.execute(
            "UPDATE cof_card SET review_status = 'approved' WHERE deck_id = %s AND review_status = 'pending'",
            (deck_id,),
        )
    conn.commit()
    print(f"Approved deck {deck_id} (+ pending PMVs/cards in deck)")


def reject_deck(conn, deck_id: int) -> None:
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE cof_deck SET review_status = 'rejected', enabled = FALSE WHERE id = %s",
            (deck_id,),
        )
    conn.commit()
    print(f"Rejected deck {deck_id}")


def approve_pmv(conn, pmv_row_id: int) -> None:
    with conn.cursor() as cur:
        cur.execute("UPDATE cof_deck_pmv SET review_status = 'approved' WHERE id = %s", (pmv_row_id,))
    conn.commit()
    print(f"Approved PMV row {pmv_row_id}")


def reject_pmv(conn, pmv_row_id: int) -> None:
    with conn.cursor() as cur:
        cur.execute("UPDATE cof_deck_pmv SET review_status = 'rejected' WHERE id = %s", (pmv_row_id,))
    conn.commit()
    print(f"Rejected PMV row {pmv_row_id}")


def approve_card(conn, card_row_id: int) -> None:
    with conn.cursor() as cur:
        cur.execute("UPDATE cof_card SET review_status = 'approved' WHERE id = %s", (card_row_id,))
    conn.commit()
    print(f"Approved card row {card_row_id}")


def reject_card(conn, card_row_id: int) -> None:
    with conn.cursor() as cur:
        cur.execute("UPDATE cof_card SET review_status = 'rejected' WHERE id = %s", (card_row_id,))
    conn.commit()
    print(f"Rejected card row {card_row_id}")


def prompt_action() -> None:
    print(
        """
Commands:
  ad <deck_id>     Approve deck (and pending PMVs/cards in that deck)
  rd <deck_id>     Reject deck
  ap <pmv_row_id>  Approve PMV row
  rp <pmv_row_id>  Reject PMV row
  ac <card_row_id> Approve card row
  rc <card_row_id> Reject card row
  r              Refresh list
  q              Quit
"""
    )


def main() -> None:
    conn = connect()
    print(f"Connected to {DATABASE_URL.split('@')[-1]}")
    try:
        while True:
            pending = fetch_pending(conn)
            print_section(
                "Pending decks",
                pending["decks"],
                lambda r: f"#{r['id']} {r['name']} ({r['folder_name']}) by {r['submitter_client_id']} cards={r['card_count']}",
            )
            print_section(
                "Pending PMVs",
                pending["pmvs"],
                lambda r: f"#{r['id']} deck={r['deck_id']} pmv={r['pmv_id']} {r['name']}",
            )
            print_section(
                "Pending cards",
                pending["cards"],
                lambda r: f"#{r['id']} deck={r['deck_id']} pmv={r['pmv_id']} shot={r['shot']} {r['image_url']}",
            )
            prompt_action()
            line = input("> ").strip()
            if not line:
                continue
            if line == "q":
                break
            if line == "r":
                continue
            parts = line.split()
            cmd = parts[0].lower()
            if len(parts) < 2:
                print("Need an id.")
                continue
            try:
                entity_id = int(parts[1])
            except ValueError:
                print("Id must be integer.")
                continue
            if cmd == "ad":
                approve_deck(conn, entity_id)
            elif cmd == "rd":
                reject_deck(conn, entity_id)
            elif cmd == "ap":
                approve_pmv(conn, entity_id)
            elif cmd == "rp":
                reject_pmv(conn, entity_id)
            elif cmd == "ac":
                approve_card(conn, entity_id)
            elif cmd == "rc":
                reject_card(conn, entity_id)
            else:
                print("Unknown command.")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
