--
-- PostgreSQL database dump
--

\restrict 6qvkgMtiLOENHeSQ9fZJV6OyFcTdjoSbaQ3kghfjyHhj9BtLhGSZ81KgTdrl5vE

-- Dumped from database version 14.20 (Homebrew)
-- Dumped by pg_dump version 14.20 (Homebrew)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: gig_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.gig_type AS ENUM (
    'practice',
    'open_mic',
    'busking',
    'booked',
    'gig',
    'showcase',
    'private_party',
    'informal_performance'
);


--
-- Name: set_gig_type_from_flags(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_gig_type_from_flags() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF NEW.gig_type IS NULL THEN
    NEW.gig_type := CASE
      WHEN NEW.openmic IS TRUE THEN 'open_mic'::gig_type
      WHEN NEW.free IS TRUE THEN 'busking'::gig_type
      ELSE 'booked'::gig_type
    END;
  END IF;
  RETURN NEW;
END;
$$;


--
-- Name: set_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: album_songs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.album_songs (
    album_id integer NOT NULL,
    song_title character varying(50) NOT NULL,
    track_number integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: albums; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.albums (
    id integer NOT NULL,
    title character varying(80) NOT NULL,
    artist character varying(80),
    release_date date,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: albums_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.albums_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: albums_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.albums_id_seq OWNED BY public.albums.id;


--
-- Name: performances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.performances (
    id integer NOT NULL,
    performancedate date DEFAULT ('now'::text)::date NOT NULL,
    venue character varying(80) NOT NULL,
    free boolean DEFAULT true,
    openmic boolean DEFAULT true,
    gig_type public.gig_type NOT NULL,
    fee_micro_gbp bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: song_performances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.song_performances (
    id integer NOT NULL,
    song_id text NOT NULL,
    performance_id integer NOT NULL,
    setlistposition integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: best_day; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.best_day AS
 SELECT nums.weekday,
    ((nums.num_song_performances)::double precision / (nums.num_performances)::double precision) AS song_performances_per_performance
   FROM ( SELECT to_char((performances.performancedate)::timestamp with time zone, 'day'::text) AS weekday,
            count(DISTINCT song_performances.id) AS num_song_performances,
            count(DISTINCT performances.id) AS num_performances
           FROM public.song_performances,
            public.performances
          WHERE (song_performances.performance_id = performances.id)
          GROUP BY (to_char((performances.performancedate)::timestamp with time zone, 'day'::text))) nums
  ORDER BY ((nums.num_song_performances)::double precision / (nums.num_performances)::double precision) DESC;


--
-- Name: best_day_om; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.best_day_om AS
 SELECT nums.weekday,
    ((nums.num_song_performances)::double precision / (nums.num_performances)::double precision) AS song_performances_per_performance
   FROM ( SELECT to_char((performances.performancedate)::timestamp with time zone, 'day'::text) AS weekday,
            count(DISTINCT song_performances.id) AS num_song_performances,
            count(DISTINCT performances.id) AS num_performances
           FROM public.song_performances,
            public.performances
          WHERE ((song_performances.performance_id = performances.id) AND (performances.openmic = true))
          GROUP BY (to_char((performances.performancedate)::timestamp with time zone, 'day'::text))) nums
  ORDER BY ((nums.num_song_performances)::double precision / (nums.num_performances)::double precision) DESC;


--
-- Name: best_venue; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.best_venue AS
 SELECT nums.venue,
    ((nums.num_song_performances)::double precision / (nums.num_performances)::double precision) AS song_performances_per_performance
   FROM ( SELECT performances.venue,
            count(DISTINCT song_performances.id) AS num_song_performances,
            count(DISTINCT performances.id) AS num_performances
           FROM public.song_performances,
            public.performances
          WHERE (song_performances.performance_id = performances.id)
          GROUP BY performances.venue) nums
  ORDER BY ((nums.num_song_performances)::double precision / (nums.num_performances)::double precision) DESC;


--
-- Name: best_venue_om; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.best_venue_om AS
 SELECT nums.venue,
    ((nums.num_song_performances)::double precision / (nums.num_performances)::double precision) AS song_performances_per_performance
   FROM ( SELECT performances.venue,
            count(DISTINCT song_performances.id) AS num_song_performances,
            count(DISTINCT performances.id) AS num_performances
           FROM public.song_performances,
            public.performances
          WHERE ((song_performances.performance_id = performances.id) AND (performances.openmic = true))
          GROUP BY performances.venue) nums
  ORDER BY ((nums.num_song_performances)::double precision / (nums.num_performances)::double precision) DESC;


--
-- Name: favourite_venue; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.favourite_venue AS
 SELECT performances.venue,
    count(DISTINCT performances.id) AS num_performances
   FROM public.performances
  GROUP BY performances.venue
  ORDER BY (count(DISTINCT performances.id)) DESC;


--
-- Name: home_x_goals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.home_x_goals (
    goal_key text NOT NULL,
    target_value integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT home_x_goals_target_value_check CHECK ((target_value > 0))
);


--
-- Name: instruments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.instruments (
    id integer NOT NULL,
    name text NOT NULL,
    manufacturer text,
    model text,
    family text,
    arrange_category text,
    competence text,
    next_step text
);


--
-- Name: instruments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.instruments_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: instruments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.instruments_id_seq OWNED BY public.instruments.id;


--
-- Name: songs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.songs (
    title character varying(50) NOT NULL,
    length interval minute to second,
    active boolean DEFAULT true,
    cover boolean DEFAULT false,
    instrumental boolean DEFAULT false,
    key text,
    artist text,
    bpm integer,
    recorded_key text,
    my_live_key text,
    capo integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: view_song_last_played; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_song_last_played AS
 SELECT DISTINCT ON (song_performances.song_id) song_performances.song_id,
    age((performances.performancedate)::timestamp with time zone) AS last_played
   FROM public.song_performances,
    public.performances
  WHERE (performances.id = song_performances.performance_id)
  ORDER BY song_performances.song_id, performances.performancedate DESC;


--
-- Name: view_song_plays; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_song_plays AS
 SELECT song_performances.song_id,
    count(song_performances.song_id) AS count
   FROM public.song_performances
  GROUP BY song_performances.song_id
  ORDER BY (count(song_performances.song_id)) DESC;


--
-- Name: next_song; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.next_song AS
 SELECT (s.title)::text AS song_id,
    COALESCE(vsp.count, (0)::bigint) AS count,
    vslp.last_played,
    s.active,
    s.cover
   FROM ((public.songs s
     LEFT JOIN public.view_song_plays vsp ON ((vsp.song_id = (s.title)::text)))
     LEFT JOIN public.view_song_last_played vslp ON ((vslp.song_id = (s.title)::text)))
  ORDER BY COALESCE(vsp.count, (0)::bigint), vslp.last_played DESC;


--
-- Name: next_active_songs; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.next_active_songs AS
 SELECT next_song.song_id,
    next_song.count,
    next_song.last_played
   FROM public.next_song;


--
-- Name: original_songs_count; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.original_songs_count AS
 SELECT count(*) AS count
   FROM public.songs
  WHERE ((songs.cover = false) AND (songs.instrumental = false));


--
-- Name: performance_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.performance_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: performance_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.performance_id_seq OWNED BY public.performances.id;


--
-- Name: performances_count; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.performances_count AS
 SELECT count(*) AS count
   FROM public.performances;


--
-- Name: practice_session_songs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.practice_session_songs (
    practice_session_id integer NOT NULL,
    song_title character varying(50) NOT NULL,
    "position" integer NOT NULL,
    minutes integer,
    song_id character varying(50),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: practice_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.practice_sessions (
    id integer NOT NULL,
    practiced_on date DEFAULT CURRENT_DATE NOT NULL,
    total_minutes integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: practice_sessions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.practice_sessions_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: practice_sessions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.practice_sessions_id_seq OWNED BY public.practice_sessions.id;


--
-- Name: practice_song_details; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.practice_song_details (
    practice_session_id integer NOT NULL,
    song_title character varying(50) NOT NULL,
    instrument_id integer,
    vocal_mode_id integer,
    capo_position integer,
    used_metronome boolean,
    notes text,
    practice_posture text,
    CONSTRAINT chk_practice_song_details_capo CHECK (((capo_position IS NULL) OR ((capo_position >= 0) AND (capo_position <= 24)))),
    CONSTRAINT chk_practice_song_details_posture CHECK (((practice_posture IS NULL) OR (practice_posture = ANY (ARRAY['sitting'::text, 'standing'::text]))))
);


--
-- Name: practice_song_keys; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.practice_song_keys (
    practice_session_id integer NOT NULL,
    song_title character varying(50) NOT NULL,
    ordinal integer NOT NULL,
    key_name text NOT NULL,
    CONSTRAINT chk_practice_song_keys_key_name CHECK ((length(TRIM(BOTH FROM key_name)) > 0)),
    CONSTRAINT chk_practice_song_keys_ordinal CHECK ((ordinal > 0))
);


--
-- Name: practice_song_tempos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.practice_song_tempos (
    practice_session_id integer NOT NULL,
    song_title character varying(50) NOT NULL,
    ordinal integer NOT NULL,
    bpm integer NOT NULL,
    CONSTRAINT chk_practice_song_tempos_bpm CHECK (((bpm > 0) AND (bpm <= 400))),
    CONSTRAINT chk_practice_song_tempos_ordinal CHECK ((ordinal > 0))
);


--
-- Name: practice_vocal_modes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.practice_vocal_modes (
    id integer NOT NULL,
    code text NOT NULL,
    label text NOT NULL
);


--
-- Name: practice_vocal_modes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.practice_vocal_modes_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: practice_vocal_modes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.practice_vocal_modes_id_seq OWNED BY public.practice_vocal_modes.id;


--
-- Name: session_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.session_types (
    id integer NOT NULL,
    name text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: session_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.session_types_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: session_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.session_types_id_seq OWNED BY public.session_types.id;


--
-- Name: sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sessions (
    id integer NOT NULL,
    start timestamp without time zone,
    "end" timestamp without time zone,
    session_type_id integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: sessions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sessions_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sessions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sessions_id_seq OWNED BY public.sessions.id;


--
-- Name: singing_lesson_songs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.singing_lesson_songs (
    singing_lesson_id integer NOT NULL,
    song_title character varying(50) NOT NULL,
    "position" integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: singing_lessons; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.singing_lessons (
    id integer NOT NULL,
    lesson_date date DEFAULT CURRENT_DATE NOT NULL,
    duration_minutes integer DEFAULT 120 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: singing_lessons_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.singing_lessons_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: singing_lessons_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.singing_lessons_id_seq OWNED BY public.singing_lessons.id;


--
-- Name: song_performance_dates; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.song_performance_dates AS
 SELECT song_performances.song_id,
    performances.performancedate
   FROM (public.song_performances
     JOIN public.performances ON ((performances.id = song_performances.performance_id)));


--
-- Name: song_performances_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.song_performances_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: song_performances_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.song_performances_id_seq OWNED BY public.song_performances.id;


--
-- Name: songs_per_gig_frequencies; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.songs_per_gig_frequencies AS
 WITH x AS (
         SELECT song_performances.performance_id,
            count(song_performances.performance_id) AS count
           FROM public.song_performances
          GROUP BY song_performances.performance_id
          ORDER BY song_performances.performance_id
        )
 SELECT x.count,
    count(x.count) AS freq
   FROM x
  GROUP BY x.count
  ORDER BY x.count;


--
-- Name: venues; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.venues (
    venuename character varying(80) NOT NULL,
    postcode character varying(8),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: view_diary; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_diary AS
 SELECT performances.performancedate,
    performances.venue,
    song_performances.song_id
   FROM public.performances,
    public.song_performances
  WHERE (song_performances.performance_id = performances.id)
  ORDER BY performances.performancedate, performances.venue, song_performances.setlistposition;


--
-- Name: view_recent_sessions; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_recent_sessions AS
SELECT
    NULL::text AS session_type,
    NULL::integer AS session_id,
    NULL::date AS session_date,
    NULL::timestamp with time zone AS session_created_at,
    NULL::text AS session_label,
    NULL::bigint AS song_count,
    NULL::integer AS minimum_minutes,
    NULL::integer AS actual_minutes,
    NULL::integer AS effective_minutes,
    NULL::timestamp with time zone AS session_occurred_at;


--
-- Name: view_home_x_metrics; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_home_x_metrics AS
 WITH year_start AS (
         SELECT (date_trunc('year'::text, (CURRENT_DATE)::timestamp with time zone))::date AS d
        ), week_start AS (
         SELECT (date_trunc('week'::text, (CURRENT_DATE)::timestamp with time zone))::date AS d
        )
 SELECT (( SELECT count(*) AS count
           FROM public.performances p
          WHERE (p.performancedate >= ( SELECT year_start.d
                   FROM year_start))))::integer AS gigs_ytd,
    (( SELECT count(*) AS count
           FROM public.performances))::integer AS gigs_lifetime,
    (COALESCE(( SELECT sum(COALESCE(vrs.effective_minutes, 0)) AS sum
           FROM public.view_recent_sessions vrs
          WHERE ((vrs.session_date >= ( SELECT week_start.d
                   FROM week_start)) AND (vrs.session_type = 'solo_practice'::text))), (0)::bigint))::integer AS solo_practice_minutes_weekly,
    (COALESCE(( SELECT sum(COALESCE(vrs.effective_minutes, 0)) AS sum
           FROM public.view_recent_sessions vrs
          WHERE ((vrs.session_date >= ( SELECT year_start.d
                   FROM year_start)) AND (vrs.session_type = ANY (ARRAY['solo_practice'::text, 'singing_lesson'::text])))), (0)::bigint))::integer AS practice_minutes_ytd,
    (COALESCE(( SELECT sum(COALESCE(vrs.effective_minutes, 0)) AS sum
           FROM public.view_recent_sessions vrs
          WHERE (vrs.session_type = ANY (ARRAY['solo_practice'::text, 'singing_lesson'::text]))), (0)::bigint))::integer AS practice_minutes_lifetime,
    (COALESCE(( SELECT count(sp.song_id) AS count
           FROM ((public.song_performances sp
             JOIN public.performances p ON ((p.id = sp.performance_id)))
             JOIN public.songs s ON (((s.title)::text = sp.song_id)))
          WHERE ((p.performancedate >= ( SELECT year_start.d
                   FROM year_start)) AND (s.artist = 'Farhan Mannan'::text))), (0)::bigint))::integer AS songs_performed_live_ytd,
    (COALESCE(( SELECT count(sp.song_id) AS count
           FROM ((public.song_performances sp
             JOIN public.performances p ON ((p.id = sp.performance_id)))
             JOIN public.songs s ON (((s.title)::text = sp.song_id)))
          WHERE (s.artist = 'Farhan Mannan'::text)), (0)::bigint))::integer AS songs_performed_live_lifetime,
    (COALESCE(( SELECT count(*) AS count
           FROM public.view_recent_sessions vrs
          WHERE (vrs.session_date >= ( SELECT year_start.d
                   FROM year_start))), (0)::bigint))::integer AS sessions_ytd;


--
-- Name: view_next_songs_to_play; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_next_songs_to_play AS
 SELECT next_song.song_id,
    next_song.count,
    next_song.last_played,
    next_song.active,
    next_song.cover
   FROM public.next_song
  WHERE (next_song.active = true);


--
-- Name: view_next_songs_to_perform_live; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_next_songs_to_perform_live AS
 SELECT view_next_songs_to_play.song_id,
    view_next_songs_to_play.count,
    view_next_songs_to_play.last_played,
    view_next_songs_to_play.active,
    view_next_songs_to_play.cover
   FROM public.view_next_songs_to_play;


--
-- Name: view_song_play_events; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_song_play_events AS
 SELECT sp.song_id,
    p.performancedate AS played_on
   FROM (public.song_performances sp
     JOIN public.performances p ON ((p.id = sp.performance_id)))
UNION ALL
 SELECT (COALESCE(pss.song_id, s.title))::text AS song_id,
    ps.practiced_on AS played_on
   FROM ((public.practice_session_songs pss
     JOIN public.practice_sessions ps ON ((ps.id = pss.practice_session_id)))
     LEFT JOIN public.songs s ON (((s.title)::text = (pss.song_title)::text)))
UNION ALL
 SELECT (s.title)::text AS song_id,
    sl.lesson_date AS played_on
   FROM ((public.singing_lesson_songs sls
     JOIN public.singing_lessons sl ON ((sl.id = sls.singing_lesson_id)))
     LEFT JOIN public.songs s ON (((s.title)::text = (sls.song_title)::text)));


--
-- Name: view_song_last_played_anywhere; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_song_last_played_anywhere AS
 SELECT view_song_play_events.song_id,
    max(view_song_play_events.played_on) AS last_played_anywhere
   FROM public.view_song_play_events
  GROUP BY view_song_play_events.song_id;


--
-- Name: view_song_play_anywhere_counts; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_song_play_anywhere_counts AS
 SELECT view_song_play_events.song_id,
    count(*) AS count
   FROM public.view_song_play_events
  GROUP BY view_song_play_events.song_id;


--
-- Name: view_next_songs_to_play_anywhere; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_next_songs_to_play_anywhere AS
 SELECT s.title AS song_id,
    s.title,
    COALESCE(vsp.count, (0)::bigint) AS count,
    vslp.last_played_anywhere,
    s.active,
    s.cover
   FROM ((public.songs s
     LEFT JOIN public.view_song_play_anywhere_counts vsp ON ((vsp.song_id = (s.title)::text)))
     LEFT JOIN public.view_song_last_played_anywhere vslp ON ((vslp.song_id = (s.title)::text)))
  WHERE (s.active = true)
  ORDER BY COALESCE(vsp.count, (0)::bigint), vslp.last_played_anywhere DESC;


--
-- Name: view_next_songs_to_practise; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_next_songs_to_practise AS
 SELECT view_next_songs_to_play_anywhere.song_id,
    view_next_songs_to_play_anywhere.title,
    view_next_songs_to_play_anywhere.count,
    view_next_songs_to_play_anywhere.last_played_anywhere,
    view_next_songs_to_play_anywhere.active,
    view_next_songs_to_play_anywhere.cover
   FROM public.view_next_songs_to_play_anywhere;


--
-- Name: view_practice_last_practiced; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_practice_last_practiced AS
 SELECT COALESCE(pss.song_id, s.title) AS song_id,
    max(ps.practiced_on) AS last_practiced
   FROM ((public.practice_session_songs pss
     JOIN public.practice_sessions ps ON ((ps.id = pss.practice_session_id)))
     LEFT JOIN public.songs s ON (((s.title)::text = (pss.song_title)::text)))
  GROUP BY COALESCE(pss.song_id, s.title);


--
-- Name: view_practice_song_counts; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_practice_song_counts AS
 SELECT COALESCE(pss.song_id, s.title) AS song_id,
    count(*) AS count
   FROM (public.practice_session_songs pss
     LEFT JOIN public.songs s ON (((s.title)::text = (pss.song_title)::text)))
  GROUP BY COALESCE(pss.song_id, s.title);


--
-- Name: view_song_last_performed_live; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_song_last_performed_live AS
 SELECT sp.song_id,
    max(p.performancedate) AS last_performed_live
   FROM (public.song_performances sp
     JOIN public.performances p ON ((p.id = sp.performance_id)))
  GROUP BY sp.song_id;


--
-- Name: view_song_last_practiced; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_song_last_practiced AS
 SELECT COALESCE(pss.song_id, s.title) AS song_id,
    max(ps.practiced_on) AS last_practiced
   FROM ((public.practice_session_songs pss
     JOIN public.practice_sessions ps ON ((ps.id = pss.practice_session_id)))
     LEFT JOIN public.songs s ON (((s.title)::text = (pss.song_title)::text)))
  GROUP BY COALESCE(pss.song_id, s.title);


--
-- Name: view_song_lengths_by_date; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_song_lengths_by_date AS
 SELECT performances.performancedate,
    songs.length
   FROM ((public.song_performances
     JOIN public.performances ON ((performances.id = song_performances.performance_id)))
     JOIN public.songs ON (((songs.title)::text = song_performances.song_id)));


--
-- Name: view_song_perform_live_counts; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_song_perform_live_counts AS
 SELECT sp.song_id,
    count(*) AS live_count
   FROM public.song_performances sp
  GROUP BY sp.song_id;


--
-- Name: view_song_practice_counts; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_song_practice_counts AS
 SELECT COALESCE(pss.song_id, s.title) AS song_id,
    count(*) AS practice_count
   FROM (public.practice_session_songs pss
     LEFT JOIN public.songs s ON (((s.title)::text = (pss.song_title)::text)))
  GROUP BY COALESCE(pss.song_id, s.title);


--
-- Name: view_songs_per_date; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.view_songs_per_date AS
SELECT
    NULL::timestamp with time zone AS performancedate,
    NULL::bigint AS count;


--
-- Name: albums id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.albums ALTER COLUMN id SET DEFAULT nextval('public.albums_id_seq'::regclass);


--
-- Name: instruments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.instruments ALTER COLUMN id SET DEFAULT nextval('public.instruments_id_seq'::regclass);


--
-- Name: performances id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.performances ALTER COLUMN id SET DEFAULT nextval('public.performance_id_seq'::regclass);


--
-- Name: practice_sessions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_sessions ALTER COLUMN id SET DEFAULT nextval('public.practice_sessions_id_seq'::regclass);


--
-- Name: practice_vocal_modes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_vocal_modes ALTER COLUMN id SET DEFAULT nextval('public.practice_vocal_modes_id_seq'::regclass);


--
-- Name: session_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_types ALTER COLUMN id SET DEFAULT nextval('public.session_types_id_seq'::regclass);


--
-- Name: sessions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sessions ALTER COLUMN id SET DEFAULT nextval('public.sessions_id_seq'::regclass);


--
-- Name: singing_lessons id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.singing_lessons ALTER COLUMN id SET DEFAULT nextval('public.singing_lessons_id_seq'::regclass);


--
-- Name: song_performances id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.song_performances ALTER COLUMN id SET DEFAULT nextval('public.song_performances_id_seq'::regclass);


--
-- Name: album_songs album_songs_album_id_track_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.album_songs
    ADD CONSTRAINT album_songs_album_id_track_number_key UNIQUE (album_id, track_number);


--
-- Name: album_songs album_songs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.album_songs
    ADD CONSTRAINT album_songs_pkey PRIMARY KEY (album_id, song_title);


--
-- Name: albums albums_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.albums
    ADD CONSTRAINT albums_pkey PRIMARY KEY (id);


--
-- Name: albums albums_title_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.albums
    ADD CONSTRAINT albums_title_key UNIQUE (title);


--
-- Name: home_x_goals home_x_goals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.home_x_goals
    ADD CONSTRAINT home_x_goals_pkey PRIMARY KEY (goal_key);


--
-- Name: instruments instruments_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.instruments
    ADD CONSTRAINT instruments_name_key UNIQUE (name);


--
-- Name: instruments instruments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.instruments
    ADD CONSTRAINT instruments_pkey PRIMARY KEY (id);


--
-- Name: performances performance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.performances
    ADD CONSTRAINT performance_pkey PRIMARY KEY (id);


--
-- Name: practice_session_songs practice_session_songs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_session_songs
    ADD CONSTRAINT practice_session_songs_pkey PRIMARY KEY (practice_session_id, song_title);


--
-- Name: practice_sessions practice_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_sessions
    ADD CONSTRAINT practice_sessions_pkey PRIMARY KEY (id);


--
-- Name: practice_song_details practice_song_details_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_song_details
    ADD CONSTRAINT practice_song_details_pkey PRIMARY KEY (practice_session_id, song_title);


--
-- Name: practice_song_keys practice_song_keys_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_song_keys
    ADD CONSTRAINT practice_song_keys_pkey PRIMARY KEY (practice_session_id, song_title, ordinal);


--
-- Name: practice_song_tempos practice_song_tempos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_song_tempos
    ADD CONSTRAINT practice_song_tempos_pkey PRIMARY KEY (practice_session_id, song_title, ordinal);


--
-- Name: practice_vocal_modes practice_vocal_modes_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_vocal_modes
    ADD CONSTRAINT practice_vocal_modes_code_key UNIQUE (code);


--
-- Name: practice_vocal_modes practice_vocal_modes_label_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_vocal_modes
    ADD CONSTRAINT practice_vocal_modes_label_key UNIQUE (label);


--
-- Name: practice_vocal_modes practice_vocal_modes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_vocal_modes
    ADD CONSTRAINT practice_vocal_modes_pkey PRIMARY KEY (id);


--
-- Name: session_types session_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_types
    ADD CONSTRAINT session_types_pkey PRIMARY KEY (id);


--
-- Name: sessions sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_pkey PRIMARY KEY (id);


--
-- Name: singing_lesson_songs singing_lesson_songs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.singing_lesson_songs
    ADD CONSTRAINT singing_lesson_songs_pkey PRIMARY KEY (singing_lesson_id, song_title);


--
-- Name: singing_lessons singing_lessons_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.singing_lessons
    ADD CONSTRAINT singing_lessons_pkey PRIMARY KEY (id);


--
-- Name: song_performances song_performances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.song_performances
    ADD CONSTRAINT song_performances_pkey PRIMARY KEY (id);


--
-- Name: songs songs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.songs
    ADD CONSTRAINT songs_pkey PRIMARY KEY (title);


--
-- Name: venues venues_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.venues
    ADD CONSTRAINT venues_pkey PRIMARY KEY (venuename);


--
-- Name: idx_practice_session_songs_song_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_practice_session_songs_song_id ON public.practice_session_songs USING btree (song_id);


--
-- Name: view_recent_sessions _RETURN; Type: RULE; Schema: public; Owner: -
--

CREATE OR REPLACE VIEW public.view_recent_sessions AS
 SELECT 'performance'::text AS session_type,
    p.id AS session_id,
    p.performancedate AS session_date,
    p.created_at AS session_created_at,
    (p.venue)::text AS session_label,
    count(sp.song_id) AS song_count,
    COALESCE((ceil((sum(COALESCE(EXTRACT(epoch FROM s.length), (240)::numeric)) / 60.0)))::integer, 0) AS minimum_minutes,
    COALESCE((ceil((sum(COALESCE(EXTRACT(epoch FROM s.length), (240)::numeric)) / 60.0)))::integer, 0) AS actual_minutes,
    COALESCE((ceil((sum(COALESCE(EXTRACT(epoch FROM s.length), (240)::numeric)) / 60.0)))::integer, 0) AS effective_minutes,
    p.occurred_at AS session_occurred_at
   FROM ((public.performances p
     JOIN public.song_performances sp ON ((sp.performance_id = p.id)))
     LEFT JOIN public.songs s ON (((s.title)::text = sp.song_id)))
  GROUP BY p.id
UNION ALL
 SELECT 'solo_practice'::text AS session_type,
    ps.id AS session_id,
    ps.practiced_on AS session_date,
    ps.created_at AS session_created_at,
    NULL::text AS session_label,
    count(pss.song_id) AS song_count,
    COALESCE((sum(COALESCE((pss.minutes)::numeric, ceil((EXTRACT(epoch FROM s.length) / 60.0)), (4)::numeric)))::integer, 0) AS minimum_minutes,
    COALESCE(ps.total_minutes, (sum(COALESCE((pss.minutes)::numeric, ceil((EXTRACT(epoch FROM s.length) / 60.0)), (4)::numeric)))::integer, 0) AS actual_minutes,
    COALESCE(ps.total_minutes, (sum(COALESCE((pss.minutes)::numeric, ceil((EXTRACT(epoch FROM s.length) / 60.0)), (4)::numeric)))::integer, 0) AS effective_minutes,
    ps.occurred_at AS session_occurred_at
   FROM ((public.practice_sessions ps
     JOIN public.practice_session_songs pss ON ((pss.practice_session_id = ps.id)))
     LEFT JOIN public.songs s ON (((s.title)::text = (COALESCE(pss.song_id, pss.song_title))::text)))
  GROUP BY ps.id
UNION ALL
 SELECT 'singing_lesson'::text AS session_type,
    sl.id AS session_id,
    sl.lesson_date AS session_date,
    sl.created_at AS session_created_at,
    NULL::text AS session_label,
    count(sls.song_title) AS song_count,
    COALESCE(sl.duration_minutes, 120) AS minimum_minutes,
    COALESCE(sl.duration_minutes, 120) AS actual_minutes,
    COALESCE(sl.duration_minutes, 120) AS effective_minutes,
    sl.occurred_at AS session_occurred_at
   FROM (public.singing_lessons sl
     LEFT JOIN public.singing_lesson_songs sls ON ((sls.singing_lesson_id = sl.id)))
  GROUP BY sl.id;


--
-- Name: view_songs_per_date _RETURN; Type: RULE; Schema: public; Owner: -
--

CREATE OR REPLACE VIEW public.view_songs_per_date AS
 WITH s AS (
         SELECT performances.performancedate,
            count(song_performances.song_id) AS count
           FROM public.performances,
            public.song_performances
          WHERE (song_performances.performance_id = performances.id)
          GROUP BY performances.id
          ORDER BY performances.performancedate
        ), q AS (
         SELECT performances.performancedate,
            count(song_performances.song_id) AS count
           FROM public.performances,
            public.song_performances
          WHERE (song_performances.performance_id = performances.id)
          GROUP BY performances.performancedate
          ORDER BY performances.performancedate
        )
 SELECT s.dte AS performancedate,
    COALESCE(q.count, (0)::bigint) AS count
   FROM (( SELECT generate_series((min(q_1.performancedate))::timestamp with time zone, (max(q_1.performancedate))::timestamp with time zone, '1 day'::interval) AS dte
           FROM q q_1) s
     LEFT JOIN q ON ((s.dte = q.performancedate)));


--
-- Name: performances performances_gig_type_default; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER performances_gig_type_default BEFORE INSERT OR UPDATE ON public.performances FOR EACH ROW EXECUTE FUNCTION public.set_gig_type_from_flags();


--
-- Name: album_songs set_updated_at_on_album_songs; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at_on_album_songs BEFORE UPDATE ON public.album_songs FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: albums set_updated_at_on_albums; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at_on_albums BEFORE UPDATE ON public.albums FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: home_x_goals set_updated_at_on_home_x_goals; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at_on_home_x_goals BEFORE UPDATE ON public.home_x_goals FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: performances set_updated_at_on_performances; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at_on_performances BEFORE UPDATE ON public.performances FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: practice_session_songs set_updated_at_on_practice_session_songs; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at_on_practice_session_songs BEFORE UPDATE ON public.practice_session_songs FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: practice_sessions set_updated_at_on_practice_sessions; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at_on_practice_sessions BEFORE UPDATE ON public.practice_sessions FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: session_types set_updated_at_on_session_types; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at_on_session_types BEFORE UPDATE ON public.session_types FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: sessions set_updated_at_on_sessions; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at_on_sessions BEFORE UPDATE ON public.sessions FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: singing_lesson_songs set_updated_at_on_singing_lesson_songs; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at_on_singing_lesson_songs BEFORE UPDATE ON public.singing_lesson_songs FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: singing_lessons set_updated_at_on_singing_lessons; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at_on_singing_lessons BEFORE UPDATE ON public.singing_lessons FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: song_performances set_updated_at_on_song_performances; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at_on_song_performances BEFORE UPDATE ON public.song_performances FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: songs set_updated_at_on_songs; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at_on_songs BEFORE UPDATE ON public.songs FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: venues set_updated_at_on_venues; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at_on_venues BEFORE UPDATE ON public.venues FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: album_songs album_songs_album_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.album_songs
    ADD CONSTRAINT album_songs_album_id_fkey FOREIGN KEY (album_id) REFERENCES public.albums(id) ON DELETE CASCADE;


--
-- Name: album_songs album_songs_song_title_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.album_songs
    ADD CONSTRAINT album_songs_song_title_fkey FOREIGN KEY (song_title) REFERENCES public.songs(title) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: practice_song_details fk_practice_song_details_song; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_song_details
    ADD CONSTRAINT fk_practice_song_details_song FOREIGN KEY (practice_session_id, song_title) REFERENCES public.practice_session_songs(practice_session_id, song_title) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: practice_song_keys fk_practice_song_keys_details; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_song_keys
    ADD CONSTRAINT fk_practice_song_keys_details FOREIGN KEY (practice_session_id, song_title) REFERENCES public.practice_song_details(practice_session_id, song_title) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: practice_song_tempos fk_practice_song_tempos_details; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_song_tempos
    ADD CONSTRAINT fk_practice_song_tempos_details FOREIGN KEY (practice_session_id, song_title) REFERENCES public.practice_song_details(practice_session_id, song_title) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: performances performances_venue_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.performances
    ADD CONSTRAINT performances_venue_fkey FOREIGN KEY (venue) REFERENCES public.venues(venuename);


--
-- Name: practice_session_songs practice_session_songs_practice_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_session_songs
    ADD CONSTRAINT practice_session_songs_practice_session_id_fkey FOREIGN KEY (practice_session_id) REFERENCES public.practice_sessions(id) ON DELETE CASCADE;


--
-- Name: practice_session_songs practice_session_songs_song_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_session_songs
    ADD CONSTRAINT practice_session_songs_song_id_fkey FOREIGN KEY (song_id) REFERENCES public.songs(title) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: practice_session_songs practice_session_songs_song_title_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_session_songs
    ADD CONSTRAINT practice_session_songs_song_title_fkey FOREIGN KEY (song_title) REFERENCES public.songs(title) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: practice_song_details practice_song_details_instrument_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_song_details
    ADD CONSTRAINT practice_song_details_instrument_id_fkey FOREIGN KEY (instrument_id) REFERENCES public.instruments(id) ON DELETE SET NULL;


--
-- Name: practice_song_details practice_song_details_vocal_mode_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.practice_song_details
    ADD CONSTRAINT practice_song_details_vocal_mode_id_fkey FOREIGN KEY (vocal_mode_id) REFERENCES public.practice_vocal_modes(id) ON DELETE SET NULL;


--
-- Name: singing_lesson_songs singing_lesson_songs_singing_lesson_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.singing_lesson_songs
    ADD CONSTRAINT singing_lesson_songs_singing_lesson_id_fkey FOREIGN KEY (singing_lesson_id) REFERENCES public.singing_lessons(id) ON DELETE CASCADE;


--
-- Name: singing_lesson_songs singing_lesson_songs_song_title_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.singing_lesson_songs
    ADD CONSTRAINT singing_lesson_songs_song_title_fkey FOREIGN KEY (song_title) REFERENCES public.songs(title) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: song_performances song_performances_performance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.song_performances
    ADD CONSTRAINT song_performances_performance_id_fkey FOREIGN KEY (performance_id) REFERENCES public.performances(id);


--
-- Name: song_performances song_performances_song_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.song_performances
    ADD CONSTRAINT song_performances_song_id_fkey FOREIGN KEY (song_id) REFERENCES public.songs(title);


--
-- PostgreSQL database dump complete
--

\unrestrict 6qvkgMtiLOENHeSQ9fZJV6OyFcTdjoSbaQ3kghfjyHhj9BtLhGSZ81KgTdrl5vE

