/***********************************
 * ANR EvalPDU
 * IFSTTAR 28_05_2010
 * @author Nicolas FORTIN, Judicaël PICAUT
 ***********************************/

package org.noisemap.core;

import org.gdms.data.DataSourceFactory;
import org.gdms.data.types.Type;
import org.gdms.data.types.TypeFactory;
import org.gdms.data.values.Value;
import org.gdms.data.values.ValueFactory;
import org.gdms.sql.function.Argument;
import org.gdms.sql.function.Arguments;
import org.gdms.sql.function.Function;
import org.gdms.sql.function.FunctionException;

public class BR_EvalSourceV1 implements Function {
	private Double getNoiseLvl(Double base, Double adj, Double speed,
			Double speedBase) {
		return base + adj * Math.log(speed / speedBase);
	}

	private Double dbaToW(Double dBA) {
		return Math.pow(10., dBA / 10.);
	}

	private Double wToDba(Double W) {
		return 10 * Math.log10(W);
	}

	private Double sumDba(Double dBA1, Double dBA2) {
		return wToDba(dbaToW(dBA1) + dbaToW(dBA2));
	}

	@Override
	public Value evaluate(DataSourceFactory dsf, Value... args)
			throws FunctionException {
		if (args.length != 3) {
			return ValueFactory.createNullValue();
		} else {
			final double speed = args[0].getAsDouble();
			final int vl_per_hour = args[1].getAsInt();
			final int pl_per_hour = args[2].getAsInt();

			// ///////////////////////
			// Noise road/tire
			// Use R2 surface
			double vl_road_lvl = getNoiseLvl(55.4, 20.1, speed, 90.);
			double pl_road_lvl = getNoiseLvl(63.4, 20., speed, 80.);

			// ///////////////////////
			// Noise motor
			double vl_motor_lvl = 0.;
			if (speed < 30.) {
				vl_motor_lvl = getNoiseLvl(36.7, -10., speed, 90.);
			} else if (speed < 110.) {
				vl_motor_lvl = getNoiseLvl(42.4, 2., speed, 90.);
			} else {
				vl_motor_lvl = getNoiseLvl(40.7, 21.3, speed, 90.);
			}

			double pl_motor_lvl = 0.;
			if (speed < 70.) {
				pl_motor_lvl = getNoiseLvl(49.6, -10., speed, 80.);
			} else {
				pl_motor_lvl = getNoiseLvl(50.4, 3., speed, 80.);
			}

			// ////////////////////////
			// Energetic SUM
			double vl_lvl = sumDba(vl_road_lvl, vl_motor_lvl) + 10
					* Math.log10(vl_per_hour);
			double pl_lvl = sumDba(pl_road_lvl, pl_motor_lvl) + 10
					* Math.log10(pl_per_hour);

			double sum_lvl = sumDba(vl_lvl, pl_lvl);
			return ValueFactory.createValue(sum_lvl);
		}
	}

	@Override
	public String getName() {
		return "BR_EvalSourceV1";
	}

	@Override
	public boolean isAggregate() {
		return false;
	}

	@Override
	public Type getType(Type[] types) {
		return TypeFactory.createType(Type.DOUBLE);
	}

	@Override
	public Arguments[] getFunctionArguments() {
		return new Arguments[] { new Arguments(Argument.NUMERIC,
				Argument.NUMERIC, Argument.NUMERIC) };
	}

	@Override
	public String getDescription() {
		return "Return the dB(A) value corresponding to speed,light and heavy vehicle parameters.";
	}

	@Override
	public String getSqlOrder() {
		return "select BR_EvalSourceV1(loadSpeed,lightVehicleCount,heavyVehicleCount) from myTable;";
	}

	@Override
	public Value getAggregateResult() {
		return null;
	}

}