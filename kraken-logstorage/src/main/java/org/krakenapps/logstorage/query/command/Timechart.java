package org.krakenapps.logstorage.query.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.krakenapps.logstorage.LogQueryCommand;
import org.krakenapps.logstorage.query.FileBufferMap;
import org.krakenapps.logstorage.query.command.Function.Sum;

public class Timechart extends LogQueryCommand {
	public static enum Span {
		Second(Calendar.SECOND, 1000L), Minute(Calendar.MINUTE, 60 * 1000L), Hour(Calendar.HOUR_OF_DAY, 60 * 60 * 1000L), Day(
				Calendar.DAY_OF_MONTH, 24 * 60 * 60 * 1000L), Week(Calendar.WEEK_OF_YEAR, 7 * 24 * 60 * 60 * 1000L), Month(
				Calendar.MONTH, 0L), Year(Calendar.YEAR, 0L);

		private int calendarField;
		private long millis;

		private Span(int calendarField, long millis) {
			this.calendarField = calendarField;
			this.millis = millis;
		}
	}

	private FileBufferMap<Date, Object[]> data;
	private Map<Date, Long> amount;
	private Span spanField;
	private int spanAmount;
	private Function[] values;
	private String clause;

	public Timechart(Function[] values, String clause) {
		this(Span.Day, 1, values, clause);
	}

	public Timechart(Span spanField, int spanAmount, Function[] values, String clause) {
		this.spanField = spanField;
		this.spanAmount = spanAmount;
		this.values = values;
		this.clause = clause;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void push(Map<String, Object> m) {
		if (data == null) {
			try {
				this.data = new FileBufferMap<Date, Object[]>(new FunctionCodec());
			} catch (IOException e) {
			}
		}
		if (amount == null)
			this.amount = new HashMap<Date, Long>();

		Date row = getKey((Date) m.get("_time"));
		if (!data.containsKey(row)) {
			Object[] v = new Object[values.length];
			for (int i = 0; i < values.length; i++)
				v[i] = new HashMap<String, Function>();
			data.put(row, v);

			Calendar c = Calendar.getInstance();
			c.setTime(row);
			c.add(spanField.calendarField, spanAmount);
			amount.put(row, c.getTimeInMillis() - row.getTime());
		}

		String key = "";

		if (clause != null) {
			if (getData(clause, m) == null)
				return;
			key = getData(clause, m).toString();
		}

		Object[] blocks = data.get(row);
		for (int i = 0; i < blocks.length; i++) {
			Map<String, Function> block = (Map<String, Function>) blocks[i];

			if (!block.containsKey(key)) {
				Function f = values[i].clone();
				if (f instanceof PerTime)
					((PerTime) f).amount = amount.get(row);
				block.put(key, f);
			}

			Function func = block.get(key);
			getData(func.getTarget(), m);
			func.put(m);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void eof() {
		if (data != null) {
			List<Date> sortedKey = new ArrayList<Date>(data.keySet());
			Collections.sort(sortedKey);
			for (Date key : sortedKey) {
				Object[] values = data.get(key);

				Map<String, Object> m = new HashMap<String, Object>();
				m.put("_time", key);
				if (values.length == 1) {
					for (String k : ((Map<String, Function>) values[0]).keySet()) {
						Function v = ((Map<String, Function>) values[0]).get(k);
						m.put(k, v.getResult());
					}
				} else {
					for (Object value : values) {
						for (String k : ((Map<String, Function>) value).keySet()) {
							Function v = ((Map<String, Function>) value).get(k);
							m.put(v.toString() + ":" + k, v.getResult());
						}
					}
				}
				write(m);
			}
			data.close();
			data = null;
		}
		amount = null;

		super.eof();
	}

	private Date getKey(Date date) {
		long time = date.getTime();

		if (spanField == Span.Second || spanField == Span.Minute || spanField == Span.Hour || spanField == Span.Day
				|| spanField == Span.Week) {
			time += 291600000L; // base to Monday, 00:00:00
			time -= time % (spanField.millis * spanAmount);
			time -= 291600000L;
		} else {
			Calendar c = Calendar.getInstance();
			c.setTimeInMillis(time - time % Span.Second.millis);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.DAY_OF_MONTH, 1);

			if (spanField == Span.Month) {
				int monthOffset = c.get(Calendar.YEAR) * 12;
				int month = monthOffset + c.get(Calendar.MONTH);
				month -= month % spanAmount;
				month -= monthOffset;
				c.add(Calendar.MONTH, month);
				time = c.getTimeInMillis();
			} else if (spanField == Span.Year) {
				int year = c.get(Calendar.YEAR);
				c.set(Calendar.YEAR, year - (year % spanAmount));
				time = c.getTimeInMillis();
			}
		}

		return new Date(time);
	}

	public static final Map<String, Class<? extends Function>> func;
	static {
		func = new HashMap<String, Class<? extends Function>>();
		func.put("per_second", PerSecond.class);
		func.put("per_minute", PerMinute.class);
		func.put("per_hour", PerHour.class);
		func.put("per_day", PerDay.class);
	}

	public static abstract class PerTime extends Sum {
		private long amount;

		abstract protected long getTimeLength();

		public long getAmount() {
			return amount;
		}

		public void setAmount(long amount) {
			this.amount = amount;
		}

		@Override
		public Object getResult() {
			if (super.getResult() == null)
				return null;
			return (Double) super.getResult() / ((double) amount / (double) getTimeLength());
		}
	}

	public static class PerSecond extends PerTime {
		@Override
		protected long getTimeLength() {
			return 1000L;
		}
	}

	public static class PerMinute extends PerTime {
		@Override
		protected long getTimeLength() {
			return 60 * 1000L;
		}
	}

	public static class PerHour extends PerTime {
		@Override
		protected long getTimeLength() {
			return 60 * 60 * 1000L;
		}
	}

	public static class PerDay extends PerTime {
		@Override
		protected long getTimeLength() {
			return 24 * 60 * 60 * 1000L;
		}
	}
}