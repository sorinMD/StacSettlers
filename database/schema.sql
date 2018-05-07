--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: extgamestates_1; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_1 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_1 OWNER TO s1328652;

--
-- Name: extgamestates_10; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_10 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_10 OWNER TO s1328652;

--
-- Name: extgamestates_11; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_11 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_11 OWNER TO s1328652;

--
-- Name: extgamestates_12; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_12 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_12 OWNER TO s1328652;

--
-- Name: extgamestates_13; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_13 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_13 OWNER TO s1328652;

--
-- Name: extgamestates_14; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_14 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_14 OWNER TO s1328652;

--
-- Name: extgamestates_15; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_15 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_15 OWNER TO s1328652;

--
-- Name: extgamestates_16; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_16 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_16 OWNER TO s1328652;

--
-- Name: extgamestates_17; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_17 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_17 OWNER TO s1328652;

--
-- Name: extgamestates_18; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_18 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_18 OWNER TO s1328652;

--
-- Name: extgamestates_19; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_19 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_19 OWNER TO s1328652;

--
-- Name: extgamestates_2; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_2 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_2 OWNER TO s1328652;

--
-- Name: extgamestates_20; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_20 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_20 OWNER TO s1328652;

--
-- Name: extgamestates_21; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_21 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_21 OWNER TO s1328652;

--
-- Name: extgamestates_22; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_22 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_22 OWNER TO s1328652;

--
-- Name: extgamestates_23; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_23 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_23 OWNER TO s1328652;

--
-- Name: extgamestates_24; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_24 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_24 OWNER TO s1328652;

--
-- Name: extgamestates_25; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_25 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_25 OWNER TO s1328652;

--
-- Name: extgamestates_26; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_26 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_26 OWNER TO s1328652;

--
-- Name: extgamestates_27; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_27 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_27 OWNER TO s1328652;

--
-- Name: extgamestates_28; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_28 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_28 OWNER TO s1328652;

--
-- Name: extgamestates_29; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_29 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_29 OWNER TO s1328652;

--
-- Name: extgamestates_3; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_3 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_3 OWNER TO s1328652;

--
-- Name: extgamestates_30; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_30 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_30 OWNER TO s1328652;

--
-- Name: extgamestates_31; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_31 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_31 OWNER TO s1328652;

--
-- Name: extgamestates_32; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_32 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_32 OWNER TO s1328652;

--
-- Name: extgamestates_33; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_33 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_33 OWNER TO s1328652;

--
-- Name: extgamestates_34; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_34 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_34 OWNER TO s1328652;

--
-- Name: extgamestates_35; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_35 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_35 OWNER TO s1328652;

--
-- Name: extgamestates_36; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_36 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_36 OWNER TO s1328652;

--
-- Name: extgamestates_37; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_37 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_37 OWNER TO s1328652;

--
-- Name: extgamestates_38; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_38 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_38 OWNER TO s1328652;

--
-- Name: extgamestates_39; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_39 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_39 OWNER TO s1328652;

--
-- Name: extgamestates_4; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_4 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_4 OWNER TO s1328652;

--
-- Name: extgamestates_40; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_40 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_40 OWNER TO s1328652;

--
-- Name: extgamestates_41; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_41 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_41 OWNER TO s1328652;

--
-- Name: extgamestates_42; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_42 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_42 OWNER TO s1328652;

--
-- Name: extgamestates_43; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_43 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_43 OWNER TO s1328652;

--
-- Name: extgamestates_44; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_44 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_44 OWNER TO s1328652;

--
-- Name: extgamestates_45; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_45 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_45 OWNER TO s1328652;

--
-- Name: extgamestates_46; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_46 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_46 OWNER TO s1328652;

--
-- Name: extgamestates_47; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_47 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_47 OWNER TO s1328652;

--
-- Name: extgamestates_48; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_48 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_48 OWNER TO s1328652;

--
-- Name: extgamestates_49; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_49 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_49 OWNER TO s1328652;

--
-- Name: extgamestates_5; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_5 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_5 OWNER TO s1328652;

--
-- Name: extgamestates_50; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_50 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_50 OWNER TO s1328652;

--
-- Name: extgamestates_51; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_51 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_51 OWNER TO s1328652;

--
-- Name: extgamestates_52; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_52 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_52 OWNER TO s1328652;

--
-- Name: extgamestates_53; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_53 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_53 OWNER TO s1328652;

--
-- Name: extgamestates_54; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_54 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_54 OWNER TO s1328652;

--
-- Name: extgamestates_55; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_55 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_55 OWNER TO s1328652;

--
-- Name: extgamestates_56; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_56 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_56 OWNER TO s1328652;

--
-- Name: extgamestates_57; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_57 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_57 OWNER TO s1328652;

--
-- Name: extgamestates_58; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_58 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_58 OWNER TO s1328652;

--
-- Name: extgamestates_59; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_59 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_59 OWNER TO s1328652;

--
-- Name: extgamestates_6; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_6 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_6 OWNER TO s1328652;

--
-- Name: extgamestates_60; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_60 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_60 OWNER TO s1328652;

--
-- Name: extgamestates_7; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_7 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_7 OWNER TO s1328652;

--
-- Name: extgamestates_8; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_8 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_8 OWNER TO s1328652;

--
-- Name: extgamestates_9; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE extgamestates_9 (
    id integer NOT NULL,
    name text NOT NULL,
    pasttrades integer[],
    futuretrades integer[],
    pastpbp integer[],
    futurepbp integer[],
    etw integer[],
    avgetb integer[],
    settlementetb integer[],
    roadetb integer[],
    cityetb integer[],
    devcardetb integer[],
    connterr integer[],
    notisoterr integer[],
    longestroads integer[],
    longestposroads integer[],
    disttoopp integer[],
    disttoport integer[],
    disttolegal integer[],
    rsstypeandno integer[]
);


ALTER TABLE extgamestates_9 OWNER TO s1328652;

--
-- Name: gameactions_1; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_1 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_1 OWNER TO s1328652;

--
-- Name: gameactions_10; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_10 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_10 OWNER TO s1328652;

--
-- Name: gameactions_11; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_11 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_11 OWNER TO s1328652;

--
-- Name: gameactions_12; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_12 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_12 OWNER TO s1328652;

--
-- Name: gameactions_13; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_13 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_13 OWNER TO s1328652;

--
-- Name: gameactions_14; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_14 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_14 OWNER TO s1328652;

--
-- Name: gameactions_15; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_15 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_15 OWNER TO s1328652;

--
-- Name: gameactions_16; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_16 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_16 OWNER TO s1328652;

--
-- Name: gameactions_17; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_17 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_17 OWNER TO s1328652;

--
-- Name: gameactions_18; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_18 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_18 OWNER TO s1328652;

--
-- Name: gameactions_19; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_19 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_19 OWNER TO s1328652;

--
-- Name: gameactions_2; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_2 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_2 OWNER TO s1328652;

--
-- Name: gameactions_20; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_20 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_20 OWNER TO s1328652;

--
-- Name: gameactions_21; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_21 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_21 OWNER TO s1328652;

--
-- Name: gameactions_22; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_22 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_22 OWNER TO s1328652;

--
-- Name: gameactions_23; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_23 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_23 OWNER TO s1328652;

--
-- Name: gameactions_24; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_24 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_24 OWNER TO s1328652;

--
-- Name: gameactions_25; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_25 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_25 OWNER TO s1328652;

--
-- Name: gameactions_26; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_26 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_26 OWNER TO s1328652;

--
-- Name: gameactions_27; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_27 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_27 OWNER TO s1328652;

--
-- Name: gameactions_28; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_28 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_28 OWNER TO s1328652;

--
-- Name: gameactions_29; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_29 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_29 OWNER TO s1328652;

--
-- Name: gameactions_3; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_3 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_3 OWNER TO s1328652;

--
-- Name: gameactions_30; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_30 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_30 OWNER TO s1328652;

--
-- Name: gameactions_31; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_31 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_31 OWNER TO s1328652;

--
-- Name: gameactions_32; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_32 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_32 OWNER TO s1328652;

--
-- Name: gameactions_33; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_33 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_33 OWNER TO s1328652;

--
-- Name: gameactions_34; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_34 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_34 OWNER TO s1328652;

--
-- Name: gameactions_35; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_35 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_35 OWNER TO s1328652;

--
-- Name: gameactions_36; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_36 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_36 OWNER TO s1328652;

--
-- Name: gameactions_37; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_37 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_37 OWNER TO s1328652;

--
-- Name: gameactions_38; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_38 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_38 OWNER TO s1328652;

--
-- Name: gameactions_39; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_39 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_39 OWNER TO s1328652;

--
-- Name: gameactions_4; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_4 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_4 OWNER TO s1328652;

--
-- Name: gameactions_40; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_40 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_40 OWNER TO s1328652;

--
-- Name: gameactions_41; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_41 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_41 OWNER TO s1328652;

--
-- Name: gameactions_42; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_42 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_42 OWNER TO s1328652;

--
-- Name: gameactions_43; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_43 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_43 OWNER TO s1328652;

--
-- Name: gameactions_44; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_44 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_44 OWNER TO s1328652;

--
-- Name: gameactions_45; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_45 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_45 OWNER TO s1328652;

--
-- Name: gameactions_46; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_46 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_46 OWNER TO s1328652;

--
-- Name: gameactions_47; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_47 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_47 OWNER TO s1328652;

--
-- Name: gameactions_48; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_48 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_48 OWNER TO s1328652;

--
-- Name: gameactions_49; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_49 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_49 OWNER TO s1328652;

--
-- Name: gameactions_5; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_5 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_5 OWNER TO s1328652;

--
-- Name: gameactions_50; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_50 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_50 OWNER TO s1328652;

--
-- Name: gameactions_51; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_51 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_51 OWNER TO s1328652;

--
-- Name: gameactions_52; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_52 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_52 OWNER TO s1328652;

--
-- Name: gameactions_53; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_53 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_53 OWNER TO s1328652;

--
-- Name: gameactions_54; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_54 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_54 OWNER TO s1328652;

--
-- Name: gameactions_55; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_55 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_55 OWNER TO s1328652;

--
-- Name: gameactions_56; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_56 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_56 OWNER TO s1328652;

--
-- Name: gameactions_57; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_57 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_57 OWNER TO s1328652;

--
-- Name: gameactions_58; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_58 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_58 OWNER TO s1328652;

--
-- Name: gameactions_59; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_59 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_59 OWNER TO s1328652;

--
-- Name: gameactions_6; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_6 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_6 OWNER TO s1328652;

--
-- Name: gameactions_60; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_60 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_60 OWNER TO s1328652;

--
-- Name: gameactions_7; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_7 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_7 OWNER TO s1328652;

--
-- Name: gameactions_8; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_8 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_8 OWNER TO s1328652;

--
-- Name: gameactions_9; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE gameactions_9 (
    id integer NOT NULL,
    type double precision NOT NULL,
    beforestate integer NOT NULL,
    afterstate integer NOT NULL,
    value integer
);


ALTER TABLE gameactions_9 OWNER TO s1328652;

--
-- Name: games; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE games (
    id integer NOT NULL,
    name text NOT NULL,
    season integer NOT NULL,
    league integer NOT NULL,
    player1 integer,
    score1 integer,
    player2 integer,
    score2 integer,
    player3 integer,
    score3 integer,
    player4 integer,
    score4 integer,
    trades integer[],
    pbps integer[]
);


ALTER TABLE games OWNER TO s1328652;

--
-- Name: leagues; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE leagues (
    id integer NOT NULL,
    name text NOT NULL,
    season integer NOT NULL,
    games integer NOT NULL,
    players integer NOT NULL
);


ALTER TABLE leagues OWNER TO s1328652;

--
-- Name: obsgamestates_1; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_1 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_1 OWNER TO s1328652;

--
-- Name: obsgamestates_10; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_10 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_10 OWNER TO s1328652;

--
-- Name: obsgamestates_11; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_11 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_11 OWNER TO s1328652;

--
-- Name: obsgamestates_12; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_12 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_12 OWNER TO s1328652;

--
-- Name: obsgamestates_13; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_13 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_13 OWNER TO s1328652;

--
-- Name: obsgamestates_14; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_14 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_14 OWNER TO s1328652;

--
-- Name: obsgamestates_15; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_15 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_15 OWNER TO s1328652;

--
-- Name: obsgamestates_16; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_16 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_16 OWNER TO s1328652;

--
-- Name: obsgamestates_17; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_17 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_17 OWNER TO s1328652;

--
-- Name: obsgamestates_18; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_18 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_18 OWNER TO s1328652;

--
-- Name: obsgamestates_19; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_19 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_19 OWNER TO s1328652;

--
-- Name: obsgamestates_2; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_2 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_2 OWNER TO s1328652;

--
-- Name: obsgamestates_20; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_20 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_20 OWNER TO s1328652;

--
-- Name: obsgamestates_21; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_21 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_21 OWNER TO s1328652;

--
-- Name: obsgamestates_22; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_22 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_22 OWNER TO s1328652;

--
-- Name: obsgamestates_23; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_23 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_23 OWNER TO s1328652;

--
-- Name: obsgamestates_24; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_24 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_24 OWNER TO s1328652;

--
-- Name: obsgamestates_25; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_25 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_25 OWNER TO s1328652;

--
-- Name: obsgamestates_26; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_26 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_26 OWNER TO s1328652;

--
-- Name: obsgamestates_27; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_27 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_27 OWNER TO s1328652;

--
-- Name: obsgamestates_28; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_28 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_28 OWNER TO s1328652;

--
-- Name: obsgamestates_29; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_29 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_29 OWNER TO s1328652;

--
-- Name: obsgamestates_3; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_3 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_3 OWNER TO s1328652;

--
-- Name: obsgamestates_30; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_30 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_30 OWNER TO s1328652;

--
-- Name: obsgamestates_31; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_31 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_31 OWNER TO s1328652;

--
-- Name: obsgamestates_32; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_32 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_32 OWNER TO s1328652;

--
-- Name: obsgamestates_33; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_33 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_33 OWNER TO s1328652;

--
-- Name: obsgamestates_34; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_34 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_34 OWNER TO s1328652;

--
-- Name: obsgamestates_35; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_35 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_35 OWNER TO s1328652;

--
-- Name: obsgamestates_36; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_36 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_36 OWNER TO s1328652;

--
-- Name: obsgamestates_37; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_37 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_37 OWNER TO s1328652;

--
-- Name: obsgamestates_38; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_38 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_38 OWNER TO s1328652;

--
-- Name: obsgamestates_39; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_39 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_39 OWNER TO s1328652;

--
-- Name: obsgamestates_4; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_4 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_4 OWNER TO s1328652;

--
-- Name: obsgamestates_40; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_40 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_40 OWNER TO s1328652;

--
-- Name: obsgamestates_41; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_41 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_41 OWNER TO s1328652;

--
-- Name: obsgamestates_42; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_42 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_42 OWNER TO s1328652;

--
-- Name: obsgamestates_43; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_43 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_43 OWNER TO s1328652;

--
-- Name: obsgamestates_44; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_44 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_44 OWNER TO s1328652;

--
-- Name: obsgamestates_45; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_45 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_45 OWNER TO s1328652;

--
-- Name: obsgamestates_46; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_46 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_46 OWNER TO s1328652;

--
-- Name: obsgamestates_47; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_47 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_47 OWNER TO s1328652;

--
-- Name: obsgamestates_48; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_48 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_48 OWNER TO s1328652;

--
-- Name: obsgamestates_49; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_49 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_49 OWNER TO s1328652;

--
-- Name: obsgamestates_5; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_5 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_5 OWNER TO s1328652;

--
-- Name: obsgamestates_50; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_50 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_50 OWNER TO s1328652;

--
-- Name: obsgamestates_51; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_51 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_51 OWNER TO s1328652;

--
-- Name: obsgamestates_52; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_52 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_52 OWNER TO s1328652;

--
-- Name: obsgamestates_53; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_53 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_53 OWNER TO s1328652;

--
-- Name: obsgamestates_54; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_54 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_54 OWNER TO s1328652;

--
-- Name: obsgamestates_55; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_55 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_55 OWNER TO s1328652;

--
-- Name: obsgamestates_56; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_56 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_56 OWNER TO s1328652;

--
-- Name: obsgamestates_57; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_57 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_57 OWNER TO s1328652;

--
-- Name: obsgamestates_58; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_58 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_58 OWNER TO s1328652;

--
-- Name: obsgamestates_59; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_59 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_59 OWNER TO s1328652;

--
-- Name: obsgamestates_6; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_6 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_6 OWNER TO s1328652;

--
-- Name: obsgamestates_60; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_60 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_60 OWNER TO s1328652;

--
-- Name: obsgamestates_7; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_7 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_7 OWNER TO s1328652;

--
-- Name: obsgamestates_8; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_8 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_8 OWNER TO s1328652;

--
-- Name: obsgamestates_9; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE obsgamestates_9 (
    id integer NOT NULL,
    name text NOT NULL,
    hexlayout integer[] NOT NULL,
    numberlayout integer[] NOT NULL,
    robberhex integer NOT NULL,
    gamestate integer NOT NULL,
    devcardsleft integer NOT NULL,
    diceresult integer NOT NULL,
    startingplayer integer NOT NULL,
    currentplayer integer NOT NULL,
    playeddevcard boolean NOT NULL,
    piecesonboard integer[] NOT NULL,
    touchingnumbers integer[] NOT NULL,
    players integer[]
);


ALTER TABLE obsgamestates_9 OWNER TO s1328652;

--
-- Name: players; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE players (
    id integer NOT NULL,
    name text NOT NULL,
    seasonsplayed integer[] NOT NULL,
    leaguesplayed integer[] NOT NULL,
    nogamesplayed integer[] NOT NULL,
    nogameswon integer[] NOT NULL,
    totalvps integer[] NOT NULL
);


ALTER TABLE players OWNER TO s1328652;

--
-- Name: policy_games; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE policy_games (
    id bigint NOT NULL,
    name text,
    player1 integer,
    score1 integer,
    player2 integer,
    score2 integer,
    player3 integer,
    score3 integer,
    player4 integer,
    score4 integer
);


ALTER TABLE policy_games OWNER TO s1328652;

--
-- Name: seasons; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE seasons (
    id integer NOT NULL,
    name text NOT NULL,
    leagues integer NOT NULL,
    games integer NOT NULL,
    players integer NOT NULL
);


ALTER TABLE seasons OWNER TO s1328652;

--
-- Name: simulation_games; Type: TABLE; Schema: public; Owner: s1328652; Tablespace: 
--

CREATE TABLE simulation_games (
    id bigint NOT NULL,
    name text,
    player1 integer,
    score1 integer,
    player2 integer,
    score2 integer,
    player3 integer,
    score3 integer,
    player4 integer,
    score4 integer
);


ALTER TABLE simulation_games OWNER TO s1328652;

--
-- Name: extgamestates_10_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_10
    ADD CONSTRAINT extgamestates_10_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_11_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_11
    ADD CONSTRAINT extgamestates_11_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_12_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_12
    ADD CONSTRAINT extgamestates_12_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_13_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_13
    ADD CONSTRAINT extgamestates_13_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_14_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_14
    ADD CONSTRAINT extgamestates_14_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_15_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_15
    ADD CONSTRAINT extgamestates_15_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_16_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_16
    ADD CONSTRAINT extgamestates_16_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_17_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_17
    ADD CONSTRAINT extgamestates_17_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_18_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_18
    ADD CONSTRAINT extgamestates_18_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_19_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_19
    ADD CONSTRAINT extgamestates_19_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_1_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_1
    ADD CONSTRAINT extgamestates_1_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_20_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_20
    ADD CONSTRAINT extgamestates_20_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_21_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_21
    ADD CONSTRAINT extgamestates_21_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_22_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_22
    ADD CONSTRAINT extgamestates_22_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_23_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_23
    ADD CONSTRAINT extgamestates_23_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_24_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_24
    ADD CONSTRAINT extgamestates_24_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_25_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_25
    ADD CONSTRAINT extgamestates_25_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_26_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_26
    ADD CONSTRAINT extgamestates_26_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_27_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_27
    ADD CONSTRAINT extgamestates_27_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_28_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_28
    ADD CONSTRAINT extgamestates_28_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_29_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_29
    ADD CONSTRAINT extgamestates_29_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_2_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_2
    ADD CONSTRAINT extgamestates_2_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_30_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_30
    ADD CONSTRAINT extgamestates_30_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_31_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_31
    ADD CONSTRAINT extgamestates_31_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_32_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_32
    ADD CONSTRAINT extgamestates_32_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_33_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_33
    ADD CONSTRAINT extgamestates_33_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_34_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_34
    ADD CONSTRAINT extgamestates_34_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_35_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_35
    ADD CONSTRAINT extgamestates_35_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_36_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_36
    ADD CONSTRAINT extgamestates_36_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_37_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_37
    ADD CONSTRAINT extgamestates_37_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_38_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_38
    ADD CONSTRAINT extgamestates_38_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_39_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_39
    ADD CONSTRAINT extgamestates_39_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_3_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_3
    ADD CONSTRAINT extgamestates_3_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_40_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_40
    ADD CONSTRAINT extgamestates_40_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_41_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_41
    ADD CONSTRAINT extgamestates_41_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_42_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_42
    ADD CONSTRAINT extgamestates_42_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_43_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_43
    ADD CONSTRAINT extgamestates_43_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_44_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_44
    ADD CONSTRAINT extgamestates_44_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_45_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_45
    ADD CONSTRAINT extgamestates_45_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_46_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_46
    ADD CONSTRAINT extgamestates_46_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_47_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_47
    ADD CONSTRAINT extgamestates_47_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_48_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_48
    ADD CONSTRAINT extgamestates_48_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_49_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_49
    ADD CONSTRAINT extgamestates_49_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_4_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_4
    ADD CONSTRAINT extgamestates_4_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_50_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_50
    ADD CONSTRAINT extgamestates_50_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_51_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_51
    ADD CONSTRAINT extgamestates_51_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_52_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_52
    ADD CONSTRAINT extgamestates_52_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_53_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_53
    ADD CONSTRAINT extgamestates_53_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_54_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_54
    ADD CONSTRAINT extgamestates_54_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_55_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_55
    ADD CONSTRAINT extgamestates_55_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_56_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_56
    ADD CONSTRAINT extgamestates_56_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_57_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_57
    ADD CONSTRAINT extgamestates_57_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_58_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_58
    ADD CONSTRAINT extgamestates_58_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_59_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_59
    ADD CONSTRAINT extgamestates_59_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_5_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_5
    ADD CONSTRAINT extgamestates_5_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_60_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_60
    ADD CONSTRAINT extgamestates_60_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_6_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_6
    ADD CONSTRAINT extgamestates_6_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_7_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_7
    ADD CONSTRAINT extgamestates_7_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_8_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_8
    ADD CONSTRAINT extgamestates_8_pkey PRIMARY KEY (id);


--
-- Name: extgamestates_9_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY extgamestates_9
    ADD CONSTRAINT extgamestates_9_pkey PRIMARY KEY (id);


--
-- Name: gameactions_10_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_10
    ADD CONSTRAINT gameactions_10_pkey PRIMARY KEY (id);


--
-- Name: gameactions_11_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_11
    ADD CONSTRAINT gameactions_11_pkey PRIMARY KEY (id);


--
-- Name: gameactions_12_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_12
    ADD CONSTRAINT gameactions_12_pkey PRIMARY KEY (id);


--
-- Name: gameactions_13_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_13
    ADD CONSTRAINT gameactions_13_pkey PRIMARY KEY (id);


--
-- Name: gameactions_14_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_14
    ADD CONSTRAINT gameactions_14_pkey PRIMARY KEY (id);


--
-- Name: gameactions_15_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_15
    ADD CONSTRAINT gameactions_15_pkey PRIMARY KEY (id);


--
-- Name: gameactions_16_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_16
    ADD CONSTRAINT gameactions_16_pkey PRIMARY KEY (id);


--
-- Name: gameactions_17_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_17
    ADD CONSTRAINT gameactions_17_pkey PRIMARY KEY (id);


--
-- Name: gameactions_18_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_18
    ADD CONSTRAINT gameactions_18_pkey PRIMARY KEY (id);


--
-- Name: gameactions_19_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_19
    ADD CONSTRAINT gameactions_19_pkey PRIMARY KEY (id);


--
-- Name: gameactions_1_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_1
    ADD CONSTRAINT gameactions_1_pkey PRIMARY KEY (id);


--
-- Name: gameactions_20_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_20
    ADD CONSTRAINT gameactions_20_pkey PRIMARY KEY (id);


--
-- Name: gameactions_21_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_21
    ADD CONSTRAINT gameactions_21_pkey PRIMARY KEY (id);


--
-- Name: gameactions_22_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_22
    ADD CONSTRAINT gameactions_22_pkey PRIMARY KEY (id);


--
-- Name: gameactions_23_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_23
    ADD CONSTRAINT gameactions_23_pkey PRIMARY KEY (id);


--
-- Name: gameactions_24_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_24
    ADD CONSTRAINT gameactions_24_pkey PRIMARY KEY (id);


--
-- Name: gameactions_25_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_25
    ADD CONSTRAINT gameactions_25_pkey PRIMARY KEY (id);


--
-- Name: gameactions_26_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_26
    ADD CONSTRAINT gameactions_26_pkey PRIMARY KEY (id);


--
-- Name: gameactions_27_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_27
    ADD CONSTRAINT gameactions_27_pkey PRIMARY KEY (id);


--
-- Name: gameactions_28_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_28
    ADD CONSTRAINT gameactions_28_pkey PRIMARY KEY (id);


--
-- Name: gameactions_29_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_29
    ADD CONSTRAINT gameactions_29_pkey PRIMARY KEY (id);


--
-- Name: gameactions_2_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_2
    ADD CONSTRAINT gameactions_2_pkey PRIMARY KEY (id);


--
-- Name: gameactions_30_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_30
    ADD CONSTRAINT gameactions_30_pkey PRIMARY KEY (id);


--
-- Name: gameactions_31_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_31
    ADD CONSTRAINT gameactions_31_pkey PRIMARY KEY (id);


--
-- Name: gameactions_32_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_32
    ADD CONSTRAINT gameactions_32_pkey PRIMARY KEY (id);


--
-- Name: gameactions_33_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_33
    ADD CONSTRAINT gameactions_33_pkey PRIMARY KEY (id);


--
-- Name: gameactions_34_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_34
    ADD CONSTRAINT gameactions_34_pkey PRIMARY KEY (id);


--
-- Name: gameactions_35_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_35
    ADD CONSTRAINT gameactions_35_pkey PRIMARY KEY (id);


--
-- Name: gameactions_36_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_36
    ADD CONSTRAINT gameactions_36_pkey PRIMARY KEY (id);


--
-- Name: gameactions_37_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_37
    ADD CONSTRAINT gameactions_37_pkey PRIMARY KEY (id);


--
-- Name: gameactions_38_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_38
    ADD CONSTRAINT gameactions_38_pkey PRIMARY KEY (id);


--
-- Name: gameactions_39_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_39
    ADD CONSTRAINT gameactions_39_pkey PRIMARY KEY (id);


--
-- Name: gameactions_3_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_3
    ADD CONSTRAINT gameactions_3_pkey PRIMARY KEY (id);


--
-- Name: gameactions_40_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_40
    ADD CONSTRAINT gameactions_40_pkey PRIMARY KEY (id);


--
-- Name: gameactions_41_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_41
    ADD CONSTRAINT gameactions_41_pkey PRIMARY KEY (id);


--
-- Name: gameactions_42_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_42
    ADD CONSTRAINT gameactions_42_pkey PRIMARY KEY (id);


--
-- Name: gameactions_43_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_43
    ADD CONSTRAINT gameactions_43_pkey PRIMARY KEY (id);


--
-- Name: gameactions_44_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_44
    ADD CONSTRAINT gameactions_44_pkey PRIMARY KEY (id);


--
-- Name: gameactions_45_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_45
    ADD CONSTRAINT gameactions_45_pkey PRIMARY KEY (id);


--
-- Name: gameactions_46_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_46
    ADD CONSTRAINT gameactions_46_pkey PRIMARY KEY (id);


--
-- Name: gameactions_47_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_47
    ADD CONSTRAINT gameactions_47_pkey PRIMARY KEY (id);


--
-- Name: gameactions_48_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_48
    ADD CONSTRAINT gameactions_48_pkey PRIMARY KEY (id);


--
-- Name: gameactions_49_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_49
    ADD CONSTRAINT gameactions_49_pkey PRIMARY KEY (id);


--
-- Name: gameactions_4_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_4
    ADD CONSTRAINT gameactions_4_pkey PRIMARY KEY (id);


--
-- Name: gameactions_50_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_50
    ADD CONSTRAINT gameactions_50_pkey PRIMARY KEY (id);


--
-- Name: gameactions_51_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_51
    ADD CONSTRAINT gameactions_51_pkey PRIMARY KEY (id);


--
-- Name: gameactions_52_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_52
    ADD CONSTRAINT gameactions_52_pkey PRIMARY KEY (id);


--
-- Name: gameactions_53_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_53
    ADD CONSTRAINT gameactions_53_pkey PRIMARY KEY (id);


--
-- Name: gameactions_54_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_54
    ADD CONSTRAINT gameactions_54_pkey PRIMARY KEY (id);


--
-- Name: gameactions_55_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_55
    ADD CONSTRAINT gameactions_55_pkey PRIMARY KEY (id);


--
-- Name: gameactions_56_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_56
    ADD CONSTRAINT gameactions_56_pkey PRIMARY KEY (id);


--
-- Name: gameactions_57_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_57
    ADD CONSTRAINT gameactions_57_pkey PRIMARY KEY (id);


--
-- Name: gameactions_58_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_58
    ADD CONSTRAINT gameactions_58_pkey PRIMARY KEY (id);


--
-- Name: gameactions_59_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_59
    ADD CONSTRAINT gameactions_59_pkey PRIMARY KEY (id);


--
-- Name: gameactions_5_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_5
    ADD CONSTRAINT gameactions_5_pkey PRIMARY KEY (id);


--
-- Name: gameactions_60_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_60
    ADD CONSTRAINT gameactions_60_pkey PRIMARY KEY (id);


--
-- Name: gameactions_6_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_6
    ADD CONSTRAINT gameactions_6_pkey PRIMARY KEY (id);


--
-- Name: gameactions_7_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_7
    ADD CONSTRAINT gameactions_7_pkey PRIMARY KEY (id);


--
-- Name: gameactions_8_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_8
    ADD CONSTRAINT gameactions_8_pkey PRIMARY KEY (id);


--
-- Name: gameactions_9_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY gameactions_9
    ADD CONSTRAINT gameactions_9_pkey PRIMARY KEY (id);


--
-- Name: games_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY games
    ADD CONSTRAINT games_pkey PRIMARY KEY (id);


--
-- Name: leagues_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY leagues
    ADD CONSTRAINT leagues_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_10_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_10
    ADD CONSTRAINT obsgamestates1_10_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_11_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_11
    ADD CONSTRAINT obsgamestates1_11_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_12_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_12
    ADD CONSTRAINT obsgamestates1_12_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_13_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_13
    ADD CONSTRAINT obsgamestates1_13_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_14_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_14
    ADD CONSTRAINT obsgamestates1_14_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_15_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_15
    ADD CONSTRAINT obsgamestates1_15_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_16_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_16
    ADD CONSTRAINT obsgamestates1_16_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_17_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_17
    ADD CONSTRAINT obsgamestates1_17_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_18_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_18
    ADD CONSTRAINT obsgamestates1_18_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_19_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_19
    ADD CONSTRAINT obsgamestates1_19_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_1_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_1
    ADD CONSTRAINT obsgamestates1_1_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_20_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_20
    ADD CONSTRAINT obsgamestates1_20_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_21_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_21
    ADD CONSTRAINT obsgamestates1_21_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_22_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_22
    ADD CONSTRAINT obsgamestates1_22_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_23_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_23
    ADD CONSTRAINT obsgamestates1_23_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_24_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_24
    ADD CONSTRAINT obsgamestates1_24_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_25_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_25
    ADD CONSTRAINT obsgamestates1_25_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_26_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_26
    ADD CONSTRAINT obsgamestates1_26_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_27_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_27
    ADD CONSTRAINT obsgamestates1_27_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_28_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_28
    ADD CONSTRAINT obsgamestates1_28_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_29_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_29
    ADD CONSTRAINT obsgamestates1_29_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_2_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_2
    ADD CONSTRAINT obsgamestates1_2_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_30_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_30
    ADD CONSTRAINT obsgamestates1_30_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_31_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_31
    ADD CONSTRAINT obsgamestates1_31_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_32_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_32
    ADD CONSTRAINT obsgamestates1_32_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_33_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_33
    ADD CONSTRAINT obsgamestates1_33_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_34_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_34
    ADD CONSTRAINT obsgamestates1_34_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_35_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_35
    ADD CONSTRAINT obsgamestates1_35_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_36_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_36
    ADD CONSTRAINT obsgamestates1_36_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_37_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_37
    ADD CONSTRAINT obsgamestates1_37_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_38_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_38
    ADD CONSTRAINT obsgamestates1_38_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_39_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_39
    ADD CONSTRAINT obsgamestates1_39_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_3_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_3
    ADD CONSTRAINT obsgamestates1_3_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_40_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_40
    ADD CONSTRAINT obsgamestates1_40_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_41_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_41
    ADD CONSTRAINT obsgamestates1_41_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_42_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_42
    ADD CONSTRAINT obsgamestates1_42_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_43_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_43
    ADD CONSTRAINT obsgamestates1_43_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_44_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_44
    ADD CONSTRAINT obsgamestates1_44_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_45_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_45
    ADD CONSTRAINT obsgamestates1_45_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_46_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_46
    ADD CONSTRAINT obsgamestates1_46_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_47_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_47
    ADD CONSTRAINT obsgamestates1_47_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_48_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_48
    ADD CONSTRAINT obsgamestates1_48_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_49_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_49
    ADD CONSTRAINT obsgamestates1_49_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_4_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_4
    ADD CONSTRAINT obsgamestates1_4_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_50_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_50
    ADD CONSTRAINT obsgamestates1_50_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_51_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_51
    ADD CONSTRAINT obsgamestates1_51_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_52_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_52
    ADD CONSTRAINT obsgamestates1_52_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_53_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_53
    ADD CONSTRAINT obsgamestates1_53_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_54_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_54
    ADD CONSTRAINT obsgamestates1_54_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_55_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_55
    ADD CONSTRAINT obsgamestates1_55_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_56_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_56
    ADD CONSTRAINT obsgamestates1_56_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_57_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_57
    ADD CONSTRAINT obsgamestates1_57_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_58_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_58
    ADD CONSTRAINT obsgamestates1_58_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_59_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_59
    ADD CONSTRAINT obsgamestates1_59_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_5_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_5
    ADD CONSTRAINT obsgamestates1_5_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_60_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_60
    ADD CONSTRAINT obsgamestates1_60_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_6_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_6
    ADD CONSTRAINT obsgamestates1_6_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_7_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_7
    ADD CONSTRAINT obsgamestates1_7_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_8_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_8
    ADD CONSTRAINT obsgamestates1_8_pkey PRIMARY KEY (id);


--
-- Name: obsgamestates1_9_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY obsgamestates_9
    ADD CONSTRAINT obsgamestates1_9_pkey PRIMARY KEY (id);


--
-- Name: players_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY players
    ADD CONSTRAINT players_pkey PRIMARY KEY (id);


--
-- Name: policy_games_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY policy_games
    ADD CONSTRAINT policy_games_pkey PRIMARY KEY (id);


--
-- Name: seasons_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY seasons
    ADD CONSTRAINT seasons_pkey PRIMARY KEY (id);


--
-- Name: simulation_games_pkey; Type: CONSTRAINT; Schema: public; Owner: s1328652; Tablespace: 
--

ALTER TABLE ONLY simulation_games
    ADD CONSTRAINT simulation_games_pkey PRIMARY KEY (id);


--
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- PostgreSQL database dump complete
--

