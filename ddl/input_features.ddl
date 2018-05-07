DROP TABLE IF EXISTS input_features;

CREATE TABLE input_features (
  id SERIAL PRIMARY KEY
  ,turn SMALLINT NOT NULL
  ,total_number_of_roads SMALLINT NOT NULL
  ,total_number_of_settlements SMALLINT NOT NULL
  ,total_number_of_cities SMALLINT NOT NULL
  ,total_number_of_resource_cards SMALLINT NOT NULL

  ,max_largest_army SMALLINT NOT NULL
  ,max_longest_road SMALLINT NOT NULL

-- Player specific
  ,victory_points SMALLINT NOT NULL
  ,largest_army SMALLINT NOT NULL
  ,longest_road SMALLINT NOT NULL

  ,number_of_roads SMALLINT NOT NULL
  ,number_of_settlements SMALLINT NOT NULL
  ,number_of_cities SMALLINT NOT NULL

  ,number_of_knight_development_cards SMALLINT NOT NULL
  ,number_of_discovery_development_cards SMALLINT NOT NULL
  ,number_of_monopoly_development_cards SMALLINT NOT NULL
  ,number_of_road_development_cards SMALLINT NOT NULL

  ,clay SMALLINT NOT NULL
  ,wood SMALLINT NOT NULL
  ,wheat SMALLINT NOT NULL
  ,sheep SMALLINT NOT NULL
  ,stone SMALLINT NOT NULL

  ,max_victory_points SMALLINT NOT NULL

  ,clay_access SMALLINT NOT NULL
  ,wood_access SMALLINT NOT NULL
  ,wheat_access SMALLINT NOT NULL
  ,sheep_access SMALLINT NOT NULL
  ,stone_access SMALLINT NOT NULL
  ,number_of_resource_cards SMALLINT NOT NULL

  ,clay_port_distance SMALLINT NOT NULL
  ,wood_port_distance SMALLINT NOT NULL
  ,wheat_port_distance SMALLINT NOT NULL
  ,sheep_port_distance SMALLINT NOT NULL
  ,stone_port_distance SMALLINT NOT NULL
  ,misc_port_distance SMALLINT NOT NULL

  ,robber_blocking_resources BOOLEAN NOT NULL

  ,can_build_settlement BOOLEAN NOT NULL
  ,can_build_city BOOLEAN NOT NULL
  ,can_build_road BOOLEAN NOT NULL
  ,can_buy_development_card BOOLEAN NOT NULL

  ,expected_time_to_win INT NOT NULL
  ,expected_time_to_build_road_ignoring_robber SMALLINT NOT NULL
  ,expected_time_to_build_road_with_robber SMALLINT NOT NULL
  ,expected_time_to_build_settlement_ignoring_robber SMALLINT NOT NULL
  ,expected_time_to_build_settlement_with_robber SMALLINT NOT NULL
  ,expected_time_to_build_city_ignoring_robber SMALLINT NOT NULL
  ,expected_time_to_build_city_with_robber SMALLINT NOT NULL

  ,different_number_count SMALLINT NOT NULL

  ,agent_configuration varchar(256) NOT NULL

  ,build_settlement BOOLEAN NOT NULL
  ,build_city BOOLEAN NOT NULL
  ,build_road BOOLEAN NOT NULL
  ,play_knight BOOLEAN NOT NULL
  ,play_monopoly BOOLEAN NOT NULL
  ,play_road BOOLEAN NOT NULL
  ,play_discovery_pick BOOLEAN NOT NULL
  ,buy_development_card BOOLEAN NOT NULL
)