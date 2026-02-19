#!/usr/bin/env python3
"""
Seed manul_test with realistic synthetic data.

Uses Faker for best-practice pseudo-random fixture generation.
"""

from __future__ import annotations

import argparse
import random
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta, timezone
from typing import Iterable, List, Sequence, Tuple

from faker import Faker
import psycopg2
from psycopg2.extras import execute_values


GIG_TYPES: Sequence[str] = (
    "open_mic",
    "busking",
    "booked",
    "showcase",
    "private_party",
    "informal_performance",
)


@dataclass(frozen=True)
class SeedConfig:
    songs: int
    venues: int
    performances: int
    practice_sessions: int
    singing_lessons: int
    albums: int
    seed: int


def clip(text: str, limit: int) -> str:
    return text[:limit].strip()


def to_title_case(text: str) -> str:
    return " ".join(part.capitalize() for part in text.split())


def normalize_title(text: str, limit: int) -> str:
    cleaned = text.replace("'", " ").replace("-", " ").replace("  ", " ")
    return clip(to_title_case(cleaned), limit)


def unique_values(values: Iterable[str], limit: int) -> List[str]:
    seen = set()
    out: List[str] = []
    for value in values:
        if not value:
            continue
        if value in seen:
            continue
        seen.add(value)
        out.append(value)
        if len(out) >= limit:
            break
    return out


def random_date_within_years(rng: random.Random, years: int = 5) -> date:
    today = date.today()
    start = today - timedelta(days=365 * years)
    offset = rng.randint(0, (today - start).days)
    return start + timedelta(days=offset)


def random_occurred_at(rng: random.Random, d: date, hour_min: int, hour_max: int) -> datetime:
    hour = rng.randint(hour_min, hour_max)
    minute = rng.choice((0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55))
    return datetime.combine(d, time(hour, minute, tzinfo=timezone.utc))


def gig_flags(gig_type: str) -> Tuple[bool, bool]:
    if gig_type == "open_mic":
        return True, True
    if gig_type == "busking":
        return True, False
    if gig_type == "informal_performance":
        return False, False
    return False, False


def build_song_pool(fake: Faker, count: int) -> List[str]:
    candidates = []
    while len(candidates) < count * 5:
        phrase = normalize_title(fake.catch_phrase(), 50)
        if 3 <= len(phrase) <= 50:
            candidates.append(phrase)
    songs = unique_values(candidates, count)
    while len(songs) < count:
        fallback = clip(f"Song {len(songs) + 1}", 50)
        if fallback not in songs:
            songs.append(fallback)
    return songs


def build_artist_name(fake: Faker, rng: random.Random) -> str:
    # Make band-style names common so fixtures reflect both solo artists and bands.
    if rng.random() < 0.45:
        prefixes = ("The", "The", "The", "Saint", "Electric", "Modern")
        adjectives = (
            "Neon",
            "Silver",
            "Golden",
            "Velvet",
            "Midnight",
            "Fading",
            "Burning",
            "Crystal",
            "Quiet",
            "Wild",
        )
        nouns = (
            "Wolves",
            "Echoes",
            "Pilots",
            "Lanterns",
            "Comets",
            "Riders",
            "Roses",
            "Giants",
            "Signals",
            "Frequencies",
        )
        pattern = rng.choice(
            (
                f"{rng.choice(prefixes)} {rng.choice(adjectives)} {rng.choice(nouns)}",
                f"{rng.choice(adjectives)} {rng.choice(nouns)}",
            )
        )
        return normalize_title(pattern, 50)
    return normalize_title(fake.name(), 50)


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed manul_test with synthetic data.")
    parser.add_argument("--db", default="manul_test")
    parser.add_argument("--user", default="postgres")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=5432)
    parser.add_argument("--password", default="")
    parser.add_argument("--songs", type=int, default=160)
    parser.add_argument("--venues", type=int, default=35)
    parser.add_argument("--performances", type=int, default=280)
    parser.add_argument("--practice-sessions", type=int, default=420)
    parser.add_argument("--singing-lessons", type=int, default=120)
    parser.add_argument("--albums", type=int, default=16)
    parser.add_argument("--seed", type=int, default=20260218)
    args = parser.parse_args()

    if args.db != "manul_test":
        raise SystemExit(
            f"Refusing to seed non-test database '{args.db}'. "
            "Use --db manul_test."
        )

    cfg = SeedConfig(
        songs=args.songs,
        venues=args.venues,
        performances=args.performances,
        practice_sessions=args.practice_sessions,
        singing_lessons=args.singing_lessons,
        albums=args.albums,
        seed=args.seed,
    )

    rng = random.Random(cfg.seed)
    fake = Faker("en_GB")
    Faker.seed(cfg.seed)

    conn = psycopg2.connect(
        dbname=args.db,
        user=args.user,
        host=args.host,
        port=args.port,
        password=args.password,
    )
    conn.autocommit = False

    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                TRUNCATE TABLE
                  practice_song_keys,
                  practice_song_tempos,
                  practice_song_details,
                  practice_session_songs,
                  practice_sessions,
                  singing_lesson_songs,
                  singing_lessons,
                  song_performances,
                  performances,
                  album_songs,
                  albums,
                  instruments,
                  songs,
                  venues,
                  home_x_goals
                RESTART IDENTITY CASCADE
                """
            )

            venues = unique_values(
                (
                    clip(f"{fake.street_name()}, {fake.city()}", 80)
                    for _ in range(cfg.venues * 4)
                ),
                cfg.venues,
            )
            venue_rows = [(v, clip(fake.postcode(), 8)) for v in venues]
            execute_values(cur, "INSERT INTO venues (venuename, postcode) VALUES %s", venue_rows)

            songs = build_song_pool(fake, cfg.songs)
            song_rows = []
            for title in songs:
                length_seconds = rng.randint(140, 360)
                mins, secs = divmod(length_seconds, 60)
                length_literal = f"00:{mins:02d}:{secs:02d}"
                song_rows.append(
                    (
                        title,
                        length_literal,
                        rng.random() < 0.85,
                        rng.random() < 0.25,
                        rng.random() < 0.2,
                        rng.choice(["C", "D", "E", "F", "G", "A", "B", "F#", "Bb"]),
                        build_artist_name(fake, rng),
                        rng.randint(70, 180),
                        rng.choice(["C", "D", "E", "F", "G", "A", "B", "F#", "Bb"]),
                        rng.choice(["C", "D", "E", "F", "G", "A", "B", "F#", "Bb"]),
                        rng.randint(0, 7),
                    )
                )
            execute_values(
                cur,
                """
                INSERT INTO songs
                  (title, length, active, cover, instrumental, key, artist, bpm, recorded_key, my_live_key, capo)
                VALUES %s
                """,
                song_rows,
            )

            goals_rows = [
                ("gigs_lifetime", 200),
                ("solo_practice_minutes_weekly", 240),
                ("practice_hours_lifetime", 2000),
                ("originals_live_lifetime", 120),
                ("direct_outreach_lifetime", 3000),
            ]
            execute_values(cur, "INSERT INTO home_x_goals (goal_key, target_value) VALUES %s", goals_rows)

            album_rows = []
            for i in range(cfg.albums):
                album_rows.append(
                    (
                        normalize_title(f"{fake.color_name()} {fake.word()} Sessions {i + 1}", 80),
                        build_artist_name(fake, rng),
                        random_date_within_years(rng, years=15),
                    )
                )
            execute_values(
                cur,
                "INSERT INTO albums (title, artist, release_date) VALUES %s",
                album_rows,
            )
            cur.execute("SELECT id FROM albums ORDER BY id")
            album_ids = [row[0] for row in cur.fetchall()]

            album_song_rows = []
            for album_id in album_ids:
                track_count = rng.randint(6, 12)
                for track_number, song in enumerate(rng.sample(songs, track_count), start=1):
                    album_song_rows.append((album_id, song, track_number))
            execute_values(
                cur,
                "INSERT INTO album_songs (album_id, song_title, track_number) VALUES %s",
                album_song_rows,
            )

            performance_rows = []
            for _ in range(cfg.performances):
                d = random_date_within_years(rng, years=5)
                gig_type = rng.choice(GIG_TYPES)
                free, openmic = gig_flags(gig_type)
                fee = 0 if free or openmic else rng.randint(500_000, 30_000_000)
                performance_rows.append(
                    (
                        d,
                        rng.choice(venues),
                        free,
                        openmic,
                        gig_type,
                        fee,
                        random_occurred_at(rng, d, 17, 23),
                    )
                )
            execute_values(
                cur,
                """
                INSERT INTO performances
                  (performancedate, venue, free, openmic, gig_type, fee_micro_gbp, occurred_at)
                VALUES %s
                """,
                performance_rows,
            )
            cur.execute("SELECT id FROM performances ORDER BY id")
            performance_ids = [row[0] for row in cur.fetchall()]

            setlist_rows = []
            for performance_id in performance_ids:
                setlist_size = rng.randint(3, 9)
                for pos, song in enumerate(rng.sample(songs, setlist_size), start=1):
                    setlist_rows.append((song, performance_id, pos))
            execute_values(
                cur,
                "INSERT INTO song_performances (song_id, performance_id, setlistposition) VALUES %s",
                setlist_rows,
            )

            practice_rows = []
            for _ in range(cfg.practice_sessions):
                d = random_date_within_years(rng, years=5)
                total_minutes = rng.choice([None, rng.randint(15, 180)])
                practice_rows.append((d, total_minutes, random_occurred_at(rng, d, 8, 22)))
            execute_values(
                cur,
                """
                INSERT INTO practice_sessions (practiced_on, total_minutes, occurred_at)
                VALUES %s
                """,
                practice_rows,
            )
            cur.execute("SELECT id FROM practice_sessions ORDER BY id")
            practice_ids = [row[0] for row in cur.fetchall()]

            practice_song_rows = []
            for practice_id in practice_ids:
                count = rng.randint(2, 7)
                chosen = rng.sample(songs, count)
                for pos, song in enumerate(chosen, start=1):
                    practice_song_rows.append(
                        (practice_id, song, pos, rng.choice([None, rng.randint(3, 20)]), song)
                    )
            execute_values(
                cur,
                """
                INSERT INTO practice_session_songs
                  (practice_session_id, song_title, position, minutes, song_id)
                VALUES %s
                """,
                practice_song_rows,
            )

            lesson_rows = []
            for _ in range(cfg.singing_lessons):
                d = random_date_within_years(rng, years=5)
                lesson_rows.append((d, rng.randint(45, 120), random_occurred_at(rng, d, 10, 20)))
            execute_values(
                cur,
                """
                INSERT INTO singing_lessons (lesson_date, duration_minutes, occurred_at)
                VALUES %s
                """,
                lesson_rows,
            )
            cur.execute("SELECT id FROM singing_lessons ORDER BY id")
            lesson_ids = [row[0] for row in cur.fetchall()]

            lesson_song_rows = []
            for lesson_id in lesson_ids:
                count = rng.randint(1, 5)
                for pos, song in enumerate(rng.sample(songs, count), start=1):
                    lesson_song_rows.append((lesson_id, song, pos))
            execute_values(
                cur,
                """
                INSERT INTO singing_lesson_songs (singing_lesson_id, song_title, position)
                VALUES %s
                """,
                lesson_song_rows,
            )

        conn.commit()

        print("Seed complete")
        print(
            f"songs={cfg.songs} venues={cfg.venues} performances={cfg.performances} "
            f"practice_sessions={cfg.practice_sessions} singing_lessons={cfg.singing_lessons}"
        )
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
