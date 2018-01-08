import java.io.FileInputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * Runs queries against a back-end database
 */
public class Query {
	private String configFilename;
	private Properties configProps = new Properties();

	private String jSQLDriver;
	private String jSQLUrl;
	private String jSQLUser;
	private String jSQLPassword;

	// DB Connection
	private Connection conn;

	// Logged In User
	private String username; // customer username is unique

	// Canned queries

	private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity FROM Flights WHERE fid = ?";
	private PreparedStatement checkFlightCapacityStatement;

	// transactions
	private static final String BEGIN_TRANSACTION_SQL = "SET TRANSACTION ISOLATION LEVEL SERIALIZABLE; BEGIN TRANSACTION;";
	private PreparedStatement beginTransactionStatement;

	private static final String COMMIT_SQL = "COMMIT TRANSACTION";
	private PreparedStatement commitTransactionStatement;

	private static final String ROLLBACK_SQL = "ROLLBACK TRANSACTION";
	private PreparedStatement rollbackTransactionStatement;

	// HashMap<String, String> usermap = new HashMap<String, String>();
	HashMap<Integer, Flight[]> itinerarymap = new HashMap<Integer, Flight[]>();

	class Flight {
		public int fid;
		public int year;
		public int monthId;
		public int dayOfMonth;
		public String carrierId;
		public String flightNum;
		public String originCity;
		public String destCity;
		public double time;
		public int capacity;
		public double price;

		@Override
		public String toString() {
			return "ID: " + fid + " Date: " + year + "-" + monthId + "-" + dayOfMonth + " Carrier: " + carrierId
					+ " Number: " + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
					+ " Capacity: " + capacity + " Price: " + price;
		}
	}

	public Query(String configFilename) {
		this.configFilename = configFilename;
	}

	/* Connection code to SQL Azure. */
	public void openConnection() throws Exception {
		configProps.load(new FileInputStream(configFilename));

		jSQLDriver = configProps.getProperty("flightservice.jdbc_driver");
		jSQLUrl = configProps.getProperty("flightservice.url");
		jSQLUser = configProps.getProperty("flightservice.sqlazure_username");
		jSQLPassword = configProps.getProperty("flightservice.sqlazure_password");

		/* load jdbc drivers */
		Class.forName(jSQLDriver).newInstance();

		/* open connections to the flights database */
		conn = DriverManager.getConnection(jSQLUrl, // database
				jSQLUser, // user
				jSQLPassword); // password

		conn.setAutoCommit(true); // by default automatically commit after each
									// statement

		/*
		 * You will also want to appropriately set the transaction's isolation
		 * level through: conn.setTransactionIsolation(...) See Connection
		 * class' JavaDoc for details.
		 * 
		 */

		conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
	}

	public void closeConnection() throws Exception {
		conn.close();
	}

	/**
	 * Clear the data in any custom tables created. Do not drop any tables and
	 * do not clear the flights table. You should clear any tables you use to
	 * store reservations and reset the next reservation ID to be 1.
	 */
	public void clearTables() {
		// your code here
		String query1 = "DELETE FROM RESERVATIONS";
		PreparedStatement preparedStmt;
		try {
			preparedStmt = conn.prepareStatement(query1);
			preparedStmt.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String query2 = "DELETE FROM USERS";
		PreparedStatement preparedStmt2;
		try {
			preparedStmt2 = conn.prepareStatement(query2);
			preparedStmt2.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * prepare all the SQL statements in this method. "preparing" a statement is
	 * almost like compiling it. Note that the parameters (with ?) are still not
	 * filled in
	 */
	public void prepareStatements() throws Exception {
		beginTransactionStatement = conn.prepareStatement(BEGIN_TRANSACTION_SQL);
		commitTransactionStatement = conn.prepareStatement(COMMIT_SQL);
		rollbackTransactionStatement = conn.prepareStatement(ROLLBACK_SQL);

		checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);

		/*
		 * add here more prepare statements for all the other queries you need
		 */
		/* . . . . . . */
	}

	/**
	 * Takes a user's username and password and attempts to log the user in.
	 *
	 * @param username
	 * @param password
	 *
	 * @return If someone has already logged in, then return "User already
	 *         logged in\n" For all other errors, return "Login failed\n".
	 *
	 *         Otherwise, return "Logged in as [username]\n".
	 */
	public String transaction_login(String username, String password) {
		if (this.username != null) {
			return "User already logged in\n";
		}
		String selectSQL = "SELECT password FROM USERS WHERE username = ?";
		PreparedStatement pstmt;
		try {
			pstmt = conn.prepareStatement(selectSQL);
			pstmt.setString(1, username);
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				String pass = rs.getString("password");
				if (pass.equals(password)) {
					this.username = username;
					itinerarymap.clear();
					return "Logged in as " + username + "\n";

				}
			}
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return "Login failed\n";
	}

	/**
	 * Implement the create user function.
	 *
	 * @param username
	 *            new user's username. User names are unique the system.
	 * @param password
	 *            new user's password.
	 * @param initAmount
	 *            initial amount to deposit into the user's account, should be
	 *            >= 0 (failure otherwise).
	 *
	 * @return either "Created user {@code username}\n" or "Failed to create
	 *         user\n" if failed.
	 */
	public String transaction_createCustomer(String username, String password, double initAmount) {
		if (initAmount < 0) {
			return "Failed to create user\n";
		}
		String selectSQL = "SELECT COUNT(*) as cnt FROM USERS WHERE username = ?";
		PreparedStatement pstmt;
		try {
			pstmt = conn.prepareStatement(selectSQL);
			pstmt.setString(1, username);
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				int count = rs.getInt("cnt");
				if (count > 0) {
					return "Failed to create user\n";
				}
			}
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			System.out.println("Failed to create user\n");
		}

		String cmd = "INSERT INTO USERS(username, password, balance) " + "VALUES(?, ?, ?)";
		PreparedStatement pstmt2;
		try {
			pstmt2 = conn.prepareStatement(cmd);
			pstmt2.setString(1, username);
			pstmt2.setString(2, password);
			pstmt2.setDouble(3, initAmount);
			pstmt2.executeUpdate();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "Created user " + username + "\n";
	}

	/**
	 * Implement the search function.
	 *
	 * Searches for flights from the given origin city to the given destination
	 * city, on the given day of the month. If {@code directFlight} is true, it
	 * only searches for direct flights, otherwise is searches for direct
	 * flights and flights with two "hops." Only searches for up to the number
	 * of itineraries given by {@code numberOfItineraries}.
	 *
	 * The results are sorted based on total flight time.
	 *
	 * @param originCity
	 * @param destinationCity
	 * @param directFlight
	 *            if true, then only search for direct flights, otherwise
	 *            include indirect flights as well
	 * @param dayOfMonth
	 * @param numberOfItineraries
	 *            number of itineraries to return
	 *
	 * @return If no itineraries were found, return "No flights match your
	 *         selection\n". If an error occurs, then return "Failed to
	 *         search\n".
	 *
	 *         Otherwise, the sorted itineraries printed in the following
	 *         format:
	 *
	 *         Itinerary [itinerary number]: [number of flights] flight(s),
	 *         [total flight time] minutes\n [first flight in itinerary]\n ...
	 *         [last flight in itinerary]\n
	 *
	 *         Each flight should be printed using the same format as in the
	 *         {@code Flight} class. Itinerary numbers in each search should
	 *         always start from 0 and increase by 1.
	 *
	 * @see Flight#toString()
	 */
	public String transaction_search(String originCity, String destinationCity, boolean directFlight, int dayOfMonth,
			int numberOfItineraries) {

		return transaction_search_safe(originCity, destinationCity, directFlight, dayOfMonth, numberOfItineraries);
	}

	public String transaction_search_safe(String originCity, String destinationCity, boolean directFlight,
			int dayOfMonth, int numberOfItineraries) {
		String safeSearchSQL;
		PreparedStatement pstmt;
		StringBuilder itinerary = new StringBuilder();
		int counter = 0;
		itinerarymap.clear();
		// one hop
		if (directFlight == true) {
			safeSearchSQL = "SELECT TOP (?) fid, year, month_id, day_of_month, carrier_id, flight_num, origin_city, dest_city, actual_time, capacity, price "
					+ "FROM Flights "
					+ "WHERE origin_city = (?) AND dest_city = (?) AND day_of_month = (?) AND canceled = (?) "
					+ "ORDER BY actual_time ASC, fid asc";
			try {
				pstmt = conn.prepareStatement(safeSearchSQL);
				pstmt.setInt(1, numberOfItineraries);
				pstmt.setString(2, originCity);
				pstmt.setString(3, destinationCity);
				pstmt.setInt(4, dayOfMonth);
				pstmt.setInt(5, 0);
				ResultSet oneHopResults = pstmt.executeQuery();

				while (oneHopResults.next()) {
					Flight f = new Flight();
					f.fid = oneHopResults.getInt("fid");
					f.year = oneHopResults.getInt("year");
					f.monthId = oneHopResults.getInt("month_id");
					f.dayOfMonth = oneHopResults.getInt("day_of_month");
					f.carrierId = oneHopResults.getString("carrier_id");
					f.flightNum = oneHopResults.getString("flight_num");
					f.originCity = oneHopResults.getString("origin_city");
					f.destCity = oneHopResults.getString("dest_city");
					f.time = oneHopResults.getDouble("actual_time");
					f.capacity = oneHopResults.getInt("capacity");
					f.price = oneHopResults.getDouble("price");
					itinerary.append("Itinerary " + counter + ": " + 1 + " flight(s), " + f.time + " minutes\n");
					itinerary.append(f.toString());
					itinerary.append("\n");

					Flight[] flightarray = new Flight[1];
					flightarray[0] = f;
					itinerarymap.put(counter, flightarray);

					// increment counter for itinerary number
					counter++;
				}
				if (counter == 0) {
					return "No flights match your selection\n";
				}
				oneHopResults.close();
				return itinerary.toString();
			} catch (SQLException e) {
			}
		} else if (directFlight == false) {
			safeSearchSQL = "SELECT TOP (?) fid, year, month_id, day_of_month, carrier_id, flight_num, origin_city, dest_city, actual_time, capacity, price "
					+ "FROM Flights "
					+ "WHERE origin_city = (?) AND dest_city = (?) AND day_of_month = (?) AND canceled = (?)"
					+ "ORDER BY actual_time ASC, fid ASC";
			try {
				pstmt = conn.prepareStatement(safeSearchSQL);
				pstmt.setInt(1, numberOfItineraries);
				pstmt.setString(2, originCity);
				pstmt.setString(3, destinationCity);
				pstmt.setInt(4, dayOfMonth);
				pstmt.setInt(5, 0);
				ResultSet oneHopResults = pstmt.executeQuery();

				while (oneHopResults.next()) {
					Flight f = new Flight();
					f.fid = oneHopResults.getInt("fid");
					f.year = oneHopResults.getInt("year");
					f.monthId = oneHopResults.getInt("month_id");
					f.dayOfMonth = oneHopResults.getInt("day_of_month");
					f.carrierId = oneHopResults.getString("carrier_id");
					f.flightNum = oneHopResults.getString("flight_num");
					f.originCity = oneHopResults.getString("origin_city");
					f.destCity = oneHopResults.getString("dest_city");
					f.time = oneHopResults.getDouble("actual_time");
					f.capacity = oneHopResults.getInt("capacity");
					f.price = oneHopResults.getDouble("price");
					itinerary.append("Itinerary " + counter + ": " + 1 + " flight(s), " + f.time + " minutes\n");
					itinerary.append(f.toString());
					itinerary.append("\n");

					Flight[] flightarray = new Flight[1];
					flightarray[0] = f;
					itinerarymap.put(counter, flightarray);

					counter++;
				}
				if (counter == 0) {
					return "No flights match your selection\n";
				}
				oneHopResults.close();
				if (counter < numberOfItineraries) {
					safeSearchSQL = "SELECT TOP (?) X.fid AS fid1, Y.fid AS fid2, X.year AS year1, Y.year AS year2, X.month_id AS month1, Y.month_id AS month2, X.day_of_month AS day1, Y.day_of_month AS day2, X.carrier_id AS carrier1, Y.carrier_id AS carrier2, X.flight_num AS flightnum1, Y.flight_num AS flightnum2, X.origin_city AS origincity1, Y.origin_city AS origincity2, X.dest_city AS destcity1, Y.dest_city AS destcity2, X.actual_time AS time1, Y.actual_time AS time2, X.capacity AS capacity1, Y.capacity AS capacity2, X.price AS price1, Y.price AS price2,(X.price + Y.price) as sum, (X.actual_time + Y.actual_time) as sumtime FROM FLIGHTS X, FLIGHTS Y WHERE X.origin_city = (?) AND X.dest_city = Y.origin_city AND Y.dest_city = (?) AND X.day_of_month = Y.day_of_month AND X.day_of_month = (?) AND X.canceled = (?) AND Y.canceled = (?) ORDER BY (X.actual_time + Y.actual_time)";
					try {
						pstmt = conn.prepareStatement(safeSearchSQL);
						pstmt.setInt(1, numberOfItineraries);
						pstmt.setString(2, originCity);
						pstmt.setString(3, destinationCity);
						pstmt.setInt(4, dayOfMonth);
						pstmt.setInt(5, 0);
						pstmt.setInt(6, 0);
						ResultSet oneHopResults1 = pstmt.executeQuery();
						while (oneHopResults1.next() && counter < numberOfItineraries) {
							Flight f1 = new Flight();
							f1.fid = oneHopResults1.getInt("fid1");
							f1.year = oneHopResults1.getInt("year1");
							f1.monthId = oneHopResults1.getInt("month1");
							f1.dayOfMonth = oneHopResults1.getInt("day1");
							f1.carrierId = oneHopResults1.getString("carrier1");
							f1.flightNum = oneHopResults1.getString("flightnum1");
							f1.originCity = oneHopResults1.getString("origincity1");
							f1.destCity = oneHopResults1.getString("destcity1");
							f1.time = oneHopResults1.getDouble("time1");
							f1.capacity = oneHopResults1.getInt("capacity1");
							f1.price = oneHopResults1.getDouble("price1");

							Flight f2 = new Flight();
							f2.fid = oneHopResults1.getInt("fid2");
							f2.year = oneHopResults1.getInt("year2");
							f2.monthId = oneHopResults1.getInt("month2");
							f2.dayOfMonth = oneHopResults1.getInt("day2");
							f2.carrierId = oneHopResults1.getString("carrier2");
							f2.flightNum = oneHopResults1.getString("flightnum2");
							f2.originCity = oneHopResults1.getString("origincity2");
							f2.destCity = oneHopResults1.getString("destcity2");
							f2.time = oneHopResults1.getDouble("time2");
							f2.capacity = oneHopResults1.getInt("capacity2");
							f2.price = oneHopResults1.getDouble("price2");
							itinerary.append("Itinerary " + counter + ": " + 2 + " flight(s), " + (f1.time + f2.time)
									+ " minutes\n");
							itinerary.append(f1.toString());
							itinerary.append("\n");
							itinerary.append(f2.toString());
							itinerary.append("\n");

							Flight[] flightarray = new Flight[2];
							flightarray[0] = f1;
							flightarray[1] = f2;
							itinerarymap.put(counter, flightarray);

							counter++;
						}
						if (counter == 0) {
							return "No flights match your selection\n";
						}
						oneHopResults1.close();
						return itinerary.toString();
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
				return itinerary.toString();
			} catch (SQLException e) {
			}

		}
		return "Failed to search\n";
	}

	/**
	 * Same as {@code transaction_search} except that it only performs single
	 * hop search and do it in an unsafe manner.
	 *
	 * @param originCity
	 * @param destinationCity
	 * @param directFlight
	 * @param dayOfMonth
	 * @param numberOfItineraries
	 *
	 * @return The search results. Note that this implementation *does not
	 *         conform* to the format required by {@code transaction_search}.
	 */
	private String transaction_search_unsafe(String originCity, String destinationCity, boolean directFlight,
			int dayOfMonth, int numberOfItineraries) {
		StringBuffer sb = new StringBuffer();

		try {
			// one hop itineraries
			String unsafeSearchSQL = "SELECT TOP (" + numberOfItineraries
					+ ") year,month_id,day_of_month,carrier_id,flight_num,origin_city,actual_time " + "FROM Flights "
					+ "WHERE origin_city = \'" + originCity + "\' AND dest_city = \'" + destinationCity
					+ "\' AND day_of_month =  " + dayOfMonth + " " + "ORDER BY actual_time ASC";

			Statement searchStatement = conn.createStatement();
			ResultSet oneHopResults = searchStatement.executeQuery(unsafeSearchSQL);

			while (oneHopResults.next()) {
				int result_year = oneHopResults.getInt("year");
				int result_monthId = oneHopResults.getInt("month_id");
				int result_dayOfMonth = oneHopResults.getInt("day_of_month");
				String result_carrierId = oneHopResults.getString("carrier_id");
				String result_flightNum = oneHopResults.getString("flight_num");
				String result_originCity = oneHopResults.getString("origin_city");
				int result_time = oneHopResults.getInt("actual_time");
				sb.append("Flight: " + result_year + "," + result_monthId + "," + result_dayOfMonth + ","
						+ result_carrierId + "," + result_flightNum + "," + result_originCity + "," + result_time);
			}
			oneHopResults.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return sb.toString();
	}

	/**
	 * Implements the book itinerary function.
	 *
	 * @param itineraryId
	 *            ID of the itinerary to book. This must be one that is returned
	 *            by search in the current session.
	 *
	 * @return If the user is not logged in, then return "Cannot book
	 *         reservations, not logged in\n". If try to book an itinerary with
	 *         invalid ID, then return "No such itinerary
	 *         {@code itineraryId}\n". If the user already has a reservation on
	 *         the same day as the one that they are trying to book now, then
	 *         return "You cannot book two flights in the same day\n". For all
	 *         other errors, return "Booking failed\n".
	 *
	 *         And if booking succeeded, return "Booked flight(s), reservation
	 *         ID: [reservationId]\n" where reservationId is a unique number in
	 *         the reservation system that starts from 1 and increments by 1
	 *         each time a successful reservation is made by any user in the
	 *         system.
	 * @throws SQLException
	 */
	public String transaction_book(int itineraryId) {

		if (this.username == null) {
			return "Cannot book reservations, not logged in\n";
		}
		if (!itinerarymap.containsKey(itineraryId)) {
			return "No such itinerary " + itineraryId + "\n";
		} else {
			Flight[] flightarray = itinerarymap.get(itineraryId);
			Flight f1 = flightarray[0];
			int date = f1.dayOfMonth;

			String selectSQL = "SELECT day_of_month FROM RESERVATIONS WHERE username = ? AND cancelled = ?";
			PreparedStatement pstmt;
			while (true) {
				try {

					conn.setAutoCommit(false);
					pstmt = conn.prepareStatement(selectSQL);
					pstmt.setString(1, username);
					pstmt.setInt(2, 0);
					ResultSet rs = pstmt.executeQuery();
					while (rs.next()) {
						int date2 = rs.getInt("day_of_month");
						if (date2 == date) {
							return "You cannot book two flights in the same day\n";
						}
					}
					String selectSQL11 = "SELECT COUNT(*) AS cnt FROM RESERVATIONS WHERE (fid1 = ? OR fid2 = ? ) AND cancelled = ?";
					PreparedStatement pstmt11;

					pstmt11 = conn.prepareStatement(selectSQL11);
					pstmt11.setInt(1, f1.fid);
					pstmt11.setInt(2, f1.fid);
					pstmt11.setInt(3, 0);
					ResultSet rs11 = pstmt11.executeQuery();
					while (rs11.next()) {
						int booked = rs11.getInt("cnt");
						if (f1.capacity - booked < 1) {
							return "Booking failed\n";
						}
					}

					if (flightarray.length > 1) {

						String selectSQL12 = "SELECT COUNT(*) AS cnt FROM RESERVATIONS WHERE (fid1 = ? OR fid2 = ? ) AND cancelled = ?";
						PreparedStatement pstmt12;

						pstmt12 = conn.prepareStatement(selectSQL12);
						pstmt12.setInt(1, flightarray[1].fid);
						pstmt12.setInt(2, flightarray[1].fid);
						pstmt12.setInt(3, 0);
						ResultSet rs12 = pstmt12.executeQuery();
						while (rs12.next()) {
							int booked2 = rs12.getInt("cnt");
							if (flightarray[1].capacity - booked2 < 1) {
								return "Booking failed\n";
							}
						}

					}

					int max_iid = 1;
					String selectSQL2 = "SELECT MAX(unique_id) AS maxid FROM RESERVATIONS";
					PreparedStatement pstmt2;

					pstmt2 = conn.prepareStatement(selectSQL2);
					ResultSet rs2 = pstmt2.executeQuery();
					while (rs2.next()) {
						max_iid = rs2.getInt("maxid");
					}

					String cmd = "INSERT INTO RESERVATIONS(unique_id, username, day_of_month, fid1, fid2, price, paid, cancelled) "
							+ "VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
					PreparedStatement pstmt3;

					pstmt3 = conn.prepareStatement(cmd);
					pstmt3.setInt(1, max_iid + 1);
					pstmt3.setString(2, this.username);
					pstmt3.setInt(3, date);
					pstmt3.setInt(4, flightarray[0].fid);
					if (flightarray.length > 1) {
						pstmt3.setInt(5, flightarray[1].fid);
					} else {
						pstmt3.setNull(5, java.sql.Types.INTEGER);
					}
					if (flightarray.length > 1) {
						pstmt3.setDouble(6, flightarray[0].price + flightarray[1].price);
					} else {
						pstmt3.setDouble(6, flightarray[0].price);
					}

					pstmt3.setInt(7, 0);
					pstmt3.setInt(8, 0);
					pstmt3.executeUpdate();
					conn.commit();
					conn.setAutoCommit(true);
					return "Booked flight(s), reservation ID: " + (max_iid + 1) + "\n";

				} catch (SQLException e) {
					// TODO Auto-generated catch block
					try {
						conn.rollback();
					} catch (SQLException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}

		}
	}

	/**
	 * Implements the reservations function.
	 *
	 * @return If no user has logged in, then return "Cannot view reservations,
	 *         not logged in\n" If the user has no reservations, then return "No
	 *         reservations found\n" For all other errors, return "Failed to
	 *         retrieve reservations\n"
	 *
	 *         Otherwise return the reservations in the following format:
	 *
	 *         Reservation [reservation ID] paid: [true or false]:\n" [flight 1
	 *         under the reservation] [flight 2 under the reservation]
	 *         Reservation [reservation ID] paid: [true or false]:\n" [flight 1
	 *         under the reservation] [flight 2 under the reservation] ...
	 *
	 *         Each flight should be printed using the same format as in the
	 *         {@code Flight} class.
	 *
	 * @see Flight#toString()
	 */
	public String transaction_reservations() {
		if (this.username == null) {
			return "Cannot view reservations, not logged in\n";
		}
		int count = -1;
		String selectSQL = "SELECT COUNT(*) AS cnt FROM RESERVATIONS WHERE username = ? AND cancelled = ?";
		PreparedStatement pstmt;
		try {
			conn.setAutoCommit(false);
			pstmt = conn.prepareStatement(selectSQL);
			pstmt.setString(1, this.username);
			pstmt.setInt(2, 0);
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				count = rs.getInt("cnt");
			}
			if (count == 0) {
				return "No reservations found\n";
			} else {
				StringBuilder sb = new StringBuilder();
				String selectSQL2 = "SELECT unique_id, paid, fid1, fid2 FROM RESERVATIONS WHERE username = ? AND cancelled = ?";
				PreparedStatement pstmt2;

				pstmt2 = conn.prepareStatement(selectSQL2);
				pstmt2.setString(1, this.username);
				pstmt2.setInt(2, 0);
				ResultSet rs2 = pstmt2.executeQuery();
				while (rs2.next()) {
					int unique_id = rs2.getInt("unique_id");
					int paid = rs2.getInt("paid");
					int fid1 = rs2.getInt("fid1");
					int fid2 = rs2.getObject("fid2") != null ? rs2.getInt("fid2") : -1;

					if (paid == 0) {
						sb.append("Reservation " + unique_id + " paid: false:\n");
					} else {
						sb.append("Reservation " + unique_id + " paid: true:\n");
					}

					String selectSQL3 = "SELECT year,month_id,day_of_month,carrier_id,flight_num,origin_city, dest_city, actual_time, capacity, price FROM FLIGHTS WHERE fid = ?";
					PreparedStatement pstmt3;

					pstmt3 = conn.prepareStatement(selectSQL3);
					pstmt3.setInt(1, fid1);
					ResultSet rs3 = pstmt3.executeQuery();
					while (rs3.next()) {
						Flight f1 = new Flight();
						f1.fid = fid1;
						f1.year = rs3.getInt("year");
						f1.monthId = rs3.getInt("month_id");
						f1.dayOfMonth = rs3.getInt("day_of_month");
						f1.carrierId = rs3.getString("carrier_id");
						f1.flightNum = rs3.getString("flight_num");
						f1.originCity = rs3.getString("origin_city");
						f1.destCity = rs3.getString("dest_city");
						f1.time = rs3.getDouble("actual_time");
						f1.capacity = rs3.getInt("capacity");
						f1.price = rs3.getDouble("price");
						sb.append(f1.toString());
						sb.append("\n");
					}

					if (fid2 != -1) {
						String selectSQL4 = "SELECT year,month_id,day_of_month,carrier_id,flight_num,origin_city, dest_city, actual_time, capacity, price FROM FLIGHTS WHERE fid = ?";
						PreparedStatement pstmt4;

						pstmt4 = conn.prepareStatement(selectSQL4);
						pstmt4.setInt(1, fid2);
						ResultSet rs4 = pstmt4.executeQuery();
						while (rs4.next()) {
							Flight f2 = new Flight();
							f2.fid = fid2;
							f2.year = rs4.getInt("year");
							f2.monthId = rs4.getInt("month_id");
							f2.dayOfMonth = rs4.getInt("day_of_month");
							f2.carrierId = rs4.getString("carrier_id");
							f2.flightNum = rs4.getString("flight_num");
							f2.originCity = rs4.getString("origin_city");
							f2.destCity = rs4.getString("dest_city");
							f2.time = rs4.getDouble("actual_time");
							f2.capacity = rs4.getInt("capacity");
							f2.price = rs4.getDouble("price");
							sb.append(f2.toString());
							sb.append("\n");
						}

					}

				}
				conn.commit();
				conn.setAutoCommit(true);
				return sb.toString();

			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			try {
				conn.rollback();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			e.printStackTrace();
		}
		try {
			conn.setAutoCommit(true);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return "Failed to retrieve reservations\n";
	}

	/**
	 * Implements the cancel operation.
	 *
	 * @param reservationId
	 *            the reservation ID to cancel
	 *
	 * @return If no user has logged in, then return "Cannot cancel
	 *         reservations, not logged in\n" For all other errors, return
	 *         "Failed to cancel reservation [reservationId]"
	 *
	 *         If successful, return "Canceled reservation [reservationId]"
	 *
	 *         Even though a reservation has been canceled, its ID should not be
	 *         reused by the system.
	 */
	public String transaction_cancel(int reservationId) {
		if (this.username == null) {
			return "Cannot cancel reservations, not logged in\n";
		}
		int count = -1;
		int paid = -1;
		double price = -1;
		String selectSQL = "SELECT COUNT(*) AS cnt FROM RESERVATIONS WHERE username = ? AND unique_id = ? AND cancelled = ?";
		PreparedStatement pstmt;
		try {
			pstmt = conn.prepareStatement(selectSQL);
			pstmt.setString(1, this.username);
			pstmt.setInt(2, reservationId);
			pstmt.setInt(3, 0);

			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				count = rs.getInt("cnt");
			}
			String selectSQL3 = "SELECT paid, price FROM RESERVATIONS WHERE username = ? AND unique_id = ? AND cancelled = ?";
			PreparedStatement pstmt3;

			pstmt3 = conn.prepareStatement(selectSQL3);
			pstmt3.setString(1, this.username);
			pstmt3.setInt(2, reservationId);
			pstmt3.setInt(3, 0);

			ResultSet rs3 = pstmt3.executeQuery();
			while (rs3.next()) {
				paid = rs3.getInt("paid");
				price = rs3.getDouble("price");

				if (count == 0) {
					return "Failed to cancel reservation " + reservationId + "\n";
				} else if (count == 1) {
					if (paid == 1) {
						double balance = -1;
						String selectSQL2 = "SELECT balance FROM USERS WHERE username = ?";
						PreparedStatement pstmt2;

						pstmt2 = conn.prepareStatement(selectSQL2);
						pstmt2.setString(1, this.username);

						ResultSet rs2 = pstmt2.executeQuery();
						while (rs2.next()) {
							balance = rs2.getDouble("balance");
						}
						PreparedStatement ps = conn
								.prepareStatement("UPDATE USERS SET balance = ? WHERE username = ? ");
						ps.setDouble(1, balance + price);
						ps.setString(2, this.username);
						ps.executeUpdate();
						ps.close();

					}
					PreparedStatement ps2 = conn.prepareStatement(
							"UPDATE RESERVATIONS SET cancelled = ? WHERE username = ? AND unique_id = ?");
					ps2.setInt(1, 1);
					ps2.setString(2, this.username);
					ps2.setInt(3, reservationId);
					ps2.executeUpdate();
					ps2.close();

					return "Canceled reservation " + reservationId + "\n";
				}
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

		}

		return "Failed to cancel reservation " + reservationId + "\n";
	}

	/**
	 * Implements the pay function.
	 *
	 * @param reservationId
	 *            the reservation to pay for.
	 *
	 * @return If no user has logged in, then return "Cannot pay, not logged
	 *         in\n" If the reservation is not found / not under the logged in
	 *         user's name, then return "Cannot find unpaid reservation
	 *         [reservationId] under user: [username]\n" If the user does not
	 *         have enough money in their account, then return "User has only
	 *         [balance] in account but itinerary costs [cost]\n" For all other
	 *         errors, return "Failed to pay for reservation [reservationId]\n"
	 *
	 *         If successful, return "Paid reservation: [reservationId]
	 *         remaining balance: [balance]\n" where [balance] is the remaining
	 *         balance in the user's account.
	 */
	public String transaction_pay(int reservationId) {
		if (this.username == null) {
			return "Cannot pay, not logged in\n";
		}
		double balance = -1;
		double price = -1;
		String selectSQL = "SELECT COUNT(*) as cnt FROM RESERVATIONS WHERE unique_id = ? AND username = ? AND paid = ? AND cancelled = ? ";
		PreparedStatement pstmt;
		int reservation = -1;
		try {
			pstmt = conn.prepareStatement(selectSQL);
			pstmt.setInt(1, reservationId);
			pstmt.setString(2, this.username);
			pstmt.setInt(3, 0);
			pstmt.setInt(4, 0);
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				reservation = rs.getInt("cnt");
			}
			if (reservation == 0) {
				return "Cannot find unpaid reservation " + reservationId + " under user: " + this.username + "\n";
			} else {
				String selectSQL2 = "SELECT balance FROM USERS WHERE USERNAME = ?";
				PreparedStatement pstmt2;
				pstmt2 = conn.prepareStatement(selectSQL2);
				pstmt2.setString(1, this.username);
				ResultSet rs2 = pstmt2.executeQuery();
				while (rs2.next()) {
					balance = rs2.getDouble("balance");
				}
				String selectSQL3 = "SELECT price FROM RESERVATIONS WHERE USERNAME = ? AND unique_id = ? AND cancelled = ?";
				PreparedStatement pstmt3;
				pstmt3 = conn.prepareStatement(selectSQL3);
				pstmt3.setString(1, this.username);
				pstmt3.setInt(2, reservationId);
				pstmt3.setInt(3, 0);
				ResultSet rs3 = pstmt3.executeQuery();
				while (rs3.next()) {
					price = rs3.getDouble("price");
				}
				if (balance < price) {
					return "User has only " + balance + " in account but itinerary costs " + price + "\n";
				} else {
					balance -= price;
					PreparedStatement ps = conn.prepareStatement("UPDATE USERS SET balance = ? WHERE username = ? ");
					ps.setDouble(1, balance);
					ps.setString(2, this.username);
					ps.executeUpdate();
					ps.close();

					PreparedStatement ps2 = conn
							.prepareStatement("UPDATE RESERVATIONS SET paid = ? WHERE unique_id = ? ");
					ps2.setInt(1, 1);
					ps2.setInt(2, reservationId);
					ps2.executeUpdate();
					ps2.close();

					return "Paid reservation: " + reservationId + " remaining balance: " + balance + "\n";
				}

			}

		} catch (SQLException e) {

			// TODO Auto-generated catch block
			e.printStackTrace();

		}

		return "Failed to pay for reservation " + reservationId + "\n";
	}

	/* some utility functions below */

	public void beginTransaction() throws SQLException {
		conn.setAutoCommit(false);
		beginTransactionStatement.executeUpdate();
	}

	public void commitTransaction() throws SQLException {
		commitTransactionStatement.executeUpdate();
		conn.setAutoCommit(true);
	}

	public void rollbackTransaction() throws SQLException {
		rollbackTransactionStatement.executeUpdate();
		conn.setAutoCommit(true);
	}

	/**
	 * Shows an example of using PreparedStatements after setting arguments. You
	 * don't need to use this method if you don't want to.
	 */
	private int checkFlightCapacity(int fid) throws SQLException {
		checkFlightCapacityStatement.clearParameters();
		checkFlightCapacityStatement.setInt(1, fid);
		ResultSet results = checkFlightCapacityStatement.executeQuery();
		results.next();
		int capacity = results.getInt("capacity");
		results.close();

		return capacity;
	}
}
