package chess.api;

import chess.api.pieces.Piece;
import com.google.common.collect.ImmutableMap;

public class FENWriter {

	private static final ImmutableMap<Integer, Character> CASTLE_POSITION_CODES = ImmutableMap.of(2, 'Q', 6, 'K', 58, 'q', 62, 'k');
	public static String write(PieceConfiguration pieceConfiguration){
		StringBuilder fenBuilder = new StringBuilder();
		//board pieces
		for(int y = 7; y >= 0; y--) {
			int gapSize = 0;
			for(int x = 0; x < 8; x++) {
				Piece piece = pieceConfiguration.getPieceAtPosition(Position.getPosition(x, y));
				if (piece != null) {
					if (gapSize > 0) {
						fenBuilder.append(gapSize);
						gapSize = 0;
					}
					fenBuilder.append(piece.getFENCode());
				} else {
					gapSize ++;
				}
			}
			if (gapSize > 0) {
				fenBuilder.append(gapSize);
			}
			if (y != 0) {
				fenBuilder.append('/');
			}
		}
		fenBuilder.append(' ');
		//current turn team
		fenBuilder.append((char)((byte)pieceConfiguration.getTurnSide().toString().charAt(0) + 32));
		fenBuilder.append(' ');
		//castling availability
		boolean castling = false;
		for(int castlePosition : pieceConfiguration.getCastlePositions()) {
			if (CASTLE_POSITION_CODES.containsKey(castlePosition)) {
				fenBuilder.append(CASTLE_POSITION_CODES.get(castlePosition));
				castling = true;
			}
		}
		if (!castling) {
			fenBuilder.append('-');
		}
		fenBuilder.append(' ');
		//en Passant piece
		if (pieceConfiguration.getEnPassantSquare() != null) {
			fenBuilder.append(Position.getCoordinateString(pieceConfiguration.getEnPassantSquare()));
		} else {
			fenBuilder.append('-');
		}
		fenBuilder.append(' ');
		fenBuilder.append(pieceConfiguration.getHalfMoveClock());
		fenBuilder.append(' ');
		fenBuilder.append(pieceConfiguration.getFullMoveNumber());
		return fenBuilder.toString();
	}
}
