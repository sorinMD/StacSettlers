DROP TABLE IF EXISTS games;
DROP TABLE IF EXISTS leagues;
DROP TABLE IF EXISTS players;
DROP TABLE IF EXISTS policy_games;
DROP TABLE IF EXISTS simulation_games;
DROP TABLE IF EXISTS seasons;

CREATE TABLE games
(
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
  pbps integer[],
  CONSTRAINT games_pkey PRIMARY KEY (id)
);

CREATE TABLE leagues
(
  id integer NOT NULL,
  name text NOT NULL,
  season integer NOT NULL,
  games integer NOT NULL,
  players integer NOT NULL,
  CONSTRAINT leagues_pkey PRIMARY KEY (id)
);

CREATE TABLE players
(
  id integer NOT NULL,
  name text NOT NULL,
  seasonsplayed integer[] NOT NULL,
  leaguesplayed integer[] NOT NULL,
  nogamesplayed integer[] NOT NULL,
  nogameswon integer[] NOT NULL,
  totalvps integer[] NOT NULL,
  CONSTRAINT players_pkey PRIMARY KEY (id)
);

CREATE TABLE policy_games
(
  id bigint NOT NULL,
  name text,
  player1 integer,
  score1 integer,
  player2 integer,
  score2 integer,
  player3 integer,
  score3 integer,
  player4 integer,
  score4 integer,
  CONSTRAINT policy_games_pkey PRIMARY KEY (id)
);

CREATE TABLE seasons
(
  id integer NOT NULL,
  name text NOT NULL,
  leagues integer NOT NULL,
  games integer NOT NULL,
  players integer NOT NULL,
  CONSTRAINT seasons_pkey PRIMARY KEY (id)
);

CREATE TABLE simulation_games
(
  id bigint NOT NULL,
  name text,
  player1 integer,
  score1 integer,
  player2 integer,
  score2 integer,
  player3 integer,
  score3 integer,
  player4 integer,
  score4 integer,
  CONSTRAINT simulation_games_pkey PRIMARY KEY (id)
);