# Deploy / ops tools

## `review_submissions.py`

Interactive CLI for approving or rejecting user-submitted card decks (outside the game API).

```bash
pip install -r deploy/requirements.txt
export DATABASE_URL=postgresql://postgres:PASSWORD@127.0.0.1:5432/cof_db
python3 deploy/review_submissions.py
```

After approving a deck, restart the backend or wait for Redis catalog cache to expire so new decks appear in matchmaking and the public catalog.

Commands: `ad` / `rd` (deck), `ap` / `rp` (PMV row), `ac` / `rc` (card row), `r` refresh, `q` quit.
